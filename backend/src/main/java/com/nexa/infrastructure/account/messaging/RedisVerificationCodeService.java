package com.nexa.infrastructure.account.messaging;

import com.nexa.application.account.port.VerificationCodeService;
import com.nexa.domain.account.vo.Email;
import com.nexa.domain.account.vo.VerificationCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 邮箱验证码服务端口 {@link VerificationCodeService} 的 Redis 实现（基础设施层，R2-02）。
 *
 * <p>用 Redis 原生 TTL（{@code opsForValue().set(key, val, ttl)}）替代内存 Map + 过期时刻判断：
 * 跨实例共享、进程重启不丢、到期自动回收（防内存泄漏）。key 加业务前缀 {@code verifycode:}。
 * 校验通过即删除（一次性消费防重放），与 {@link InMemoryVerificationCodeService} 语义一致。</p>
 *
 * <p><b>切换</b>：{@code account.store.type=redis}（默认，{@link ConditionalOnProperty#matchIfMissing()}）时
 * 装配并 {@link Primary} 生效；测试 profile 置 {@code memory} 则不创建本 bean，回落内存实现。</p>
 *
 * <p><b>降级容错</b>：验证码是注册/找回命脉，Redis 连不上时不能整体不可用。Redis 操作抛
 * {@link RuntimeException}（含连接失败）时记 WARN 并委派给内存实现兜底（与 T12 AuthCacheConfig
 * 「降级不阻断主流程」同一取舍）。</p>
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "account.store", name = "type", havingValue = "redis", matchIfMissing = true)
public class RedisVerificationCodeService implements VerificationCodeService {

    private static final Logger log = LoggerFactory.getLogger(RedisVerificationCodeService.class);

    /** 业务 key 前缀（与其他存储隔离命名空间）。 */
    private static final String KEY_PREFIX = "verifycode:";

    /** 验证码有效期：10 分钟（对齐 InMemory 实现 / PRD「未过期」语义）。 */
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;

    /** Redis 不可用时的兜底（内存实现，始终在容器内）。 */
    private final InMemoryVerificationCodeService fallback;

    public RedisVerificationCodeService(StringRedisTemplate redis,
                                        InMemoryVerificationCodeService fallback) {
        this.redis = redis;
        this.fallback = fallback;
    }

    private static String key(Email email) {
        return KEY_PREFIX + email.value();
    }

    /** {@inheritDoc} */
    @Override
    public VerificationCode issue(Email email) {
        VerificationCode code = VerificationCode.generate();
        try {
            // 同邮箱覆盖旧码（set 直接覆盖）+ 原生 TTL 到期自动回收。
            redis.opsForValue().set(key(email), code.value(), TTL);
            return code;
        } catch (RuntimeException ex) {
            log.warn("redis verifycode issue failed (degrade to memory): {}", ex.toString());
            return fallback.issue(email);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean verifyAndConsume(Email email, VerificationCode code) {
        if (code == null) {
            return false;
        }
        String key = key(email);
        String stored;
        try {
            stored = redis.opsForValue().get(key);
        } catch (RuntimeException ex) {
            log.warn("redis verifycode verify failed (degrade to memory): {}", ex.toString());
            return fallback.verifyAndConsume(email, code);
        }
        if (stored == null) {
            return false; // 无暂存 / 已过期（TTL 回收）/ 已消费。
        }
        // 常量时间比较，避免时序侧信道（复用值对象的 matches）。
        boolean matched = VerificationCode.of(stored).matches(code);
        if (matched) {
            // 一次性消费：仅匹配才删，失败不消费（用户可重试），与 InMemory 一致。
            try {
                redis.delete(key);
            } catch (RuntimeException ex) {
                log.warn("redis verifycode evict failed (ignored, TTL will reclaim): {}", ex.toString());
            }
        }
        return matched;
    }
}
