package com.nexa.infrastructure.account.messaging;

import com.nexa.application.account.port.OAuthStateStore;
import com.nexa.domain.account.vo.OAuthState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * OAuth state 暂存端口 {@link OAuthStateStore} 的 Redis 实现（基础设施层，R2-02，F-1015/F-1016）。
 *
 * <p>用 Redis 原生 TTL 替代内存 Map + 过期判断：跨实例共享（多实例部署回调可能落到另一实例）、
 * 重启不丢、到期自动回收。key 加前缀 {@code oauthstate:<token>}，value 暂存绑定的邀请码 aff
 * （aff 可空 → 存空串）。{@link #consume} 用 {@code getAndDelete} 原子取回并删除（一次性消费防重放），
 * 与 {@link InMemoryOAuthStateStore} 语义一致。</p>
 *
 * <p><b>切换</b>：{@code account.store.type=redis}（默认）时装配并 {@link Primary} 生效；
 * 测试置 {@code memory} 则回落内存实现。<b>降级</b>：Redis 抛 {@link RuntimeException} 时记 WARN
 * 并委派内存兜底（与 T12「降级不阻断」一致）。</p>
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "account.store", name = "type", havingValue = "redis", matchIfMissing = true)
public class RedisOAuthStateStore implements OAuthStateStore {

    private static final Logger log = LoggerFactory.getLogger(RedisOAuthStateStore.class);

    private static final String KEY_PREFIX = "oauthstate:";

    /** state 有效期：10 分钟（授权往返窗口，对齐 InMemory 实现）。 */
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;

    /** Redis 不可用时的兜底（内存实现）。 */
    private final InMemoryOAuthStateStore fallback;

    public RedisOAuthStateStore(StringRedisTemplate redis, InMemoryOAuthStateStore fallback) {
        this.redis = redis;
        this.fallback = fallback;
    }

    /** {@inheritDoc} */
    @Override
    public void save(OAuthState state) {
        // value 存 aff（可空 → 空串），token 作键；原生 TTL 到期自动回收。
        String aff = state.aff() == null ? "" : state.aff();
        try {
            redis.opsForValue().set(KEY_PREFIX + state.token(), aff, TTL);
        } catch (RuntimeException ex) {
            log.warn("redis oauthstate save failed (degrade to memory): {}", ex.toString());
            fallback.save(state);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<OAuthState> consume(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String aff;
        try {
            // 一次性：原子取回并删除，命中即不再可用（防重放）；未命中 / 已过期返回 null。
            aff = redis.opsForValue().getAndDelete(KEY_PREFIX + token);
        } catch (RuntimeException ex) {
            log.warn("redis oauthstate consume failed (degrade to memory): {}", ex.toString());
            return fallback.consume(token);
        }
        if (aff == null) {
            return Optional.empty();
        }
        // 空串 → 无 aff（rehydrate 会把空白归一为 null）。
        return Optional.of(OAuthState.rehydrate(token, aff));
    }
}
