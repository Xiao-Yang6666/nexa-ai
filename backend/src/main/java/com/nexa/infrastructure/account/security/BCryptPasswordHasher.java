package com.nexa.infrastructure.account.security;

import com.nexa.domain.account.vo.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 领域端口 {@link PasswordHasher} 的 BCrypt 适配实现（基础设施层）。
 *
 * <p>DDD 依赖倒置：domain 只声明哈希能力契约（{@code PasswordHasher}），具体算法在 infra 实现，
 * domain 因此不依赖 Spring Security（backend-engineer §2.3）。这里用 Spring Security 的
 * {@link BCryptPasswordEncoder}——BCrypt 自带盐、可调工作因子，是密码存储的业界默认选择。</p>
 *
 * <p>线程安全：{@link BCryptPasswordEncoder} 无可变共享状态，可被多线程（含虚拟线程）并发复用，
 * 无需额外加锁。</p>
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder;

    /**
     * 构造哈希器。
     *
     * <p>使用 BCrypt 默认强度（cost=10，2^10 轮）；如需调高在此构造参数处调整。</p>
     */
    public BCryptPasswordHasher() {
        this.encoder = new BCryptPasswordEncoder();
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException 当明文为 null（BCrypt 不接受 null 输入）
     */
    @Override
    public String hash(String rawPassword) {
        if (rawPassword == null) {
            // 不吞错：明文为 null 属于上游编排缺陷，显式抛出带上下文，避免落一个不可登录账号。
            throw new IllegalArgumentException("rawPassword must not be null for hashing");
        }
        return encoder.encode(rawPassword);
    }

    /**
     * {@inheritDoc}
     *
     * <p>明文或哈希任一为空时直接判为不匹配，不抛异常——登录失败是预期分支，
     * 由上层统一翻译为 {@code InvalidCredentialException}。</p>
     */
    @Override
    public boolean matches(String rawPassword, String hashed) {
        if (rawPassword == null || hashed == null || hashed.isBlank()) {
            return false;
        }
        return encoder.matches(rawPassword, hashed);
    }
}
