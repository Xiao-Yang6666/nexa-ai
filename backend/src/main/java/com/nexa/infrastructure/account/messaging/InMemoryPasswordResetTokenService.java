package com.nexa.infrastructure.account.messaging;

import com.nexa.application.account.port.PasswordResetTokenService;
import com.nexa.domain.account.vo.Email;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 密码重置令牌服务端口 {@link PasswordResetTokenService} 的内存桩实现（基础设施层）。
 *
 * <p>承载找回密码两段式流程：F-1006 签发暂存令牌（随重置邮件下发）、F-1007 提交时校验消费。
 * 令牌 + 有效期是带状态的副作用资源，本切片用进程内 {@link ConcurrentHashMap} 暂存，
 * <b>TODO 生产环境换 Redis</b>（原生 TTL + 集群共享 + 防内存泄漏）。当前实现单实例、
 * 进程重启即失效，仅供联调/单测打通流程。</p>
 *
 * <p>领域规则来源：PRD prd-account.md AC-3「重置令牌带过期时间，令牌有效且未过期则可重置」（F-1006/1007）。
 * TTL 取 30 分钟。令牌用 {@link SecureRandom} 生成不可预测的 URL-safe 串。校验通过即消费
 * （一次性失效）防重放。令牌绑定邮箱：校验时要求 email + token 双因子均匹配。</p>
 */
@Component
public class InMemoryPasswordResetTokenService implements PasswordResetTokenService {

    /** 重置令牌有效期：30 分钟（PRD「带过期时间」语义，生产可经配置调整）。 */
    private static final Duration TTL = Duration.ofMinutes(30);

    /** 令牌随机字节数（256 bit，足够抗暴力猜测）。 */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** token → 绑定邮箱 + 过期时刻。TODO 生产换 Redis（原生 TTL + 集群共享）。 */
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** 内部暂存条目：绑定邮箱 + 过期 epoch 毫秒。 */
    private record Entry(String email, long expiresAtEpochMilli) {
        boolean isExpired(long nowEpochMilli) {
            return nowEpochMilli > expiresAtEpochMilli;
        }
    }

    /** {@inheritDoc} */
    @Override
    public String issue(Email email) {
        // 同邮箱重复申请：清理该邮箱的旧令牌（以最新为准），避免旧链接仍可用。
        store.values().removeIf(e -> e.email().equals(email.value()));

        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        long expiresAt = Instant.now().plus(TTL).toEpochMilli();
        store.put(token, new Entry(email.value(), expiresAt));
        return token;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Email> verifyAndConsume(Email email, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Entry entry = store.get(token);
        if (entry == null) {
            return Optional.empty(); // 无暂存（伪造/已消费/从未签发）。
        }
        long now = Instant.now().toEpochMilli();
        if (entry.isExpired(now)) {
            store.remove(token, entry); // 过期清理（生产 Redis 由 TTL 自动回收）。
            return Optional.empty();
        }
        // 双因子：令牌必须绑定到提交时携带的同一邮箱，防止用 A 的令牌重置 B 的密码。
        if (!entry.email().equals(email.value())) {
            return Optional.empty();
        }
        // 一次性消费：通过即删除暂存，防同令牌重放（PRD AC-3 + 一次性语义）。
        store.remove(token, entry);
        return Optional.of(email);
    }
}
