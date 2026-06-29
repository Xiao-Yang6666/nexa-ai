package com.nexa.account.infrastructure.messaging;

import com.nexa.account.application.port.PasswordResetTokenService;
import com.nexa.account.domain.vo.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * 密码重置令牌服务端口 {@link PasswordResetTokenService} 的 Redis 实现（基础设施层，R2-02）。
 *
 * <p>用 Redis 原生 TTL 替代内存 Map + 过期判断：跨实例共享、重启不丢、到期自动回收。
 * 两个 key：{@code pwdreset:token:<token>}=email（按令牌反查邮箱、做双因子校验）与
 * {@code pwdreset:email:<email>}=token（邮箱→当前令牌索引，用于同邮箱重发时作废旧令牌，
 * 对齐 {@link InMemoryPasswordResetTokenService} 的「以最新为准、旧链接失效」语义）。
 * 校验通过即删两 key（一次性消费防重放）。</p>
 *
 * <p><b>切换</b>：{@code account.store.type=redis}（默认）时装配并 {@link Primary} 生效；
 * 测试置 {@code memory} 则回落内存实现。<b>降级</b>：Redis 抛 {@link RuntimeException} 时记 WARN
 * 并委派内存兜底（与 T12「降级不阻断」一致）。</p>
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "account.store", name = "type", havingValue = "redis", matchIfMissing = true)
public class RedisPasswordResetTokenService implements PasswordResetTokenService {

    private static final Logger log = LoggerFactory.getLogger(RedisPasswordResetTokenService.class);

    private static final String TOKEN_PREFIX = "pwdreset:token:";
    private static final String EMAIL_PREFIX = "pwdreset:email:";

    /** 重置令牌有效期：30 分钟（对齐 InMemory 实现 / PRD「带过期时间」）。 */
    private static final Duration TTL = Duration.ofMinutes(30);

    /** 令牌随机字节数（256 bit，抗暴力猜测）。 */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    /** Redis 不可用时的兜底（内存实现）。 */
    private final InMemoryPasswordResetTokenService fallback;

    public RedisPasswordResetTokenService(StringRedisTemplate redis,
                                          InMemoryPasswordResetTokenService fallback) {
        this.redis = redis;
        this.fallback = fallback;
    }

    /** {@inheritDoc} */
    @Override
    public String issue(Email email) {
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        String emailKey = EMAIL_PREFIX + email.value();
        try {
            // 同邮箱重发：作废旧令牌（删旧 token key），保证旧重置链接失效（以最新为准）。
            String oldToken = redis.opsForValue().get(emailKey);
            if (oldToken != null) {
                redis.delete(TOKEN_PREFIX + oldToken);
            }
            redis.opsForValue().set(TOKEN_PREFIX + token, email.value(), TTL);
            redis.opsForValue().set(emailKey, token, TTL);
            return token;
        } catch (RuntimeException ex) {
            log.warn("redis pwdreset issue failed (degrade to memory): {}", ex.toString());
            return fallback.issue(email);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Email> verifyAndConsume(Email email, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String tokenKey = TOKEN_PREFIX + token;
        String boundEmail;
        try {
            boundEmail = redis.opsForValue().get(tokenKey);
        } catch (RuntimeException ex) {
            log.warn("redis pwdreset verify failed (degrade to memory): {}", ex.toString());
            return fallback.verifyAndConsume(email, token);
        }
        if (boundEmail == null) {
            return Optional.empty(); // 不存在 / 已过期（TTL 回收）/ 已消费。
        }
        // 双因子：令牌须绑定到提交时携带的同一邮箱，防止用 A 的令牌重置 B 的密码。
        if (!boundEmail.equals(email.value())) {
            return Optional.empty();
        }
        // 一次性消费：删 token + email 两个 key。
        try {
            redis.delete(tokenKey);
            redis.delete(EMAIL_PREFIX + email.value());
        } catch (RuntimeException ex) {
            log.warn("redis pwdreset evict failed (ignored, TTL will reclaim): {}", ex.toString());
        }
        return Optional.of(email);
    }
}
