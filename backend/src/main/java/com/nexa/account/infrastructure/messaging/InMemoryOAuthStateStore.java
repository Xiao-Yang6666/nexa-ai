package com.nexa.account.infrastructure.messaging;

import com.nexa.account.application.port.OAuthStateStore;
import com.nexa.account.domain.vo.OAuthState;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth state 暂存端口 {@link OAuthStateStore} 的内存实现（基础设施层，F-1015/F-1016）。
 *
 * <p>承载 OAuth 授权码流程的 CSRF 防护：发起授权前 {@link #save} 暂存 state（带 TTL），
 * 回调时 {@link #consume} 按 token 取回并<b>一次性消费</b>（取出即删，防重放）。state 不存在 /
 * 已过期 / 已被消费 → 返回空，OAuth 登录用例据此判 CSRF 失败（{@code InvalidOAuthStateException}）。</p>
 *
 * <p>state + 有效期是带状态的副作用资源，本切片用进程内 {@link ConcurrentHashMap} 暂存，
 * <b>TODO 生产环境换 Redis</b>（带原生 TTL、跨实例共享、防内存泄漏）。当前实现单实例、进程重启即失效，
 * 仅供联调/单测打通流程（与 {@link InMemoryVerificationCodeService} 同一取舍，backend-engineer §2.3）。</p>
 *
 * <p>TTL 取 10 分钟：授权页跳转 → 第三方登录 → 回调的往返窗口足够，超时即失效挡住陈旧/重放回调。</p>
 */
@Component
public class InMemoryOAuthStateStore implements OAuthStateStore {

    /** state 有效期：10 分钟（授权往返窗口；生产可经配置调整）。 */
    private static final Duration TTL = Duration.ofMinutes(10);

    /** token → 暂存的 state + 过期时刻。TODO 生产换 Redis（原生 TTL + 集群共享）。 */
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** 内部暂存条目：state 值对象 + 过期 epoch 毫秒。 */
    private record Entry(OAuthState state, long expiresAtEpochMilli) {
        boolean isExpired(long nowEpochMilli) {
            return nowEpochMilli > expiresAtEpochMilli;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void save(OAuthState state) {
        long expiresAt = Instant.now().plus(TTL).toEpochMilli();
        // 以 token 为键暂存；同 token 极不可能重复（高熵随机），重复则覆盖（以最新为准）。
        store.put(state.token(), new Entry(state, expiresAt));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<OAuthState> consume(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Entry entry = store.remove(token); // 一次性：命中即删，无论是否过期都不再可用（防重放）。
        if (entry == null) {
            return Optional.empty(); // 不存在 / 已被消费。
        }
        long now = Instant.now().toEpochMilli();
        if (entry.isExpired(now)) {
            // 过期不算命中（已在 remove 时清理，生产 Redis 由 TTL 自动回收）。
            return Optional.empty();
        }
        return Optional.of(entry.state());
    }
}
