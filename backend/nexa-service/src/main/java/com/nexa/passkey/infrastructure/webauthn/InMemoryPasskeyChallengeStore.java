package com.nexa.passkey.infrastructure.webauthn;

import com.nexa.passkey.application.port.PasskeyChallengeStore;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版 challenge 暂存（基础设施层 {@link PasskeyChallengeStore} 实现）。
 *
 * <p>WebAuthn challenge 短暂（默认 5 分钟有效），一次性消费防重放。本实现用进程内 {@link ConcurrentHashMap}
 * 存储，满足单实例切片需求。<b>限制（安全声明）</b>：进程内存储不跨实例共享、重启即失效——多实例部署时
 * 应替换为分布式实现（Redis），见下方 TODO。端口抽象使替换不动应用层（backend-engineer §2.3）。</p>
 *
 * <p>TODO(F-1028~1030, 多实例): 引入 Redis 实现 {@code RedisPasskeyChallengeStore}（带 TTL + 原子 GETDEL），
 * 通过配置切换。当前单实例桩满足开发/单节点验证。</p>
 */
@Component
public class InMemoryPasskeyChallengeStore implements PasskeyChallengeStore {

    /** challenge 有效期（WebAuthn 推荐数分钟内完成 ceremony）。 */
    private static final Duration TTL = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public void put(String key, String challenge) {
        store.put(key, new Entry(challenge, Instant.now().plus(TTL)));
    }

    /** {@inheritDoc} */
    @Override
    public String consume(String key) {
        Entry entry = store.remove(key);
        if (entry == null) {
            return null;
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            // 已过期：视为不存在（已在 remove 时清除，无需额外清理）。
            return null;
        }
        return entry.challenge();
    }

    /**
     * 内部条目（challenge + 过期时刻）。
     *
     * @param challenge challenge 值
     * @param expiresAt 过期时刻
     */
    private record Entry(String challenge, Instant expiresAt) {
    }
}
