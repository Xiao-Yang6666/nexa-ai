package com.nexa.infrastructure.sensitiveverify.adapter;

import com.nexa.domain.account.exception.InvalidCredentialException;
import com.nexa.domain.account.model.User;
import com.nexa.domain.account.repository.UserRepository;
import com.nexa.domain.account.vo.PasswordHasher;
import com.nexa.domain.account.vo.RawPassword;
import com.nexa.application.sensitiveverify.port.PasswordVerificationPort;
import org.springframework.stereotype.Component;

/**
 * 密码二次验证端口的账号域桥接适配器（基础设施层，F-1038）。
 *
 * <p>实现 {@link PasswordVerificationPort}，复用账号域已就绪的用户仓储 + 密码哈希器 + 用户聚合的
 * {@code matchesPassword} 充血查询完成密码比对。跨 bounded context 的复用放在 infrastructure
 * 适配器（而非让本上下文 domain/application 依赖账号域 domain），保持上下文解耦
 * （backend-engineer §2.5 模块化单体按上下文分包，跨域桥接在基础设施层）。</p>
 *
 * <p>安全默认：用户不存在 / 密码格式非法（过短过长）一律判为不匹配（{@code false}），不抛异常、不区分原因
 * （防探测）。比对委托用户聚合（哈希不出聚合），符合充血与封装。</p>
 */
@Component
public class AccountPasswordVerificationAdapter implements PasswordVerificationPort {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    /**
     * @param userRepository 账号域用户仓储（复用）
     * @param passwordHasher 账号域密码哈希器端口（BCrypt 实现）
     */
    public AccountPasswordVerificationAdapter(UserRepository userRepository,
                                              PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    /**
     * {@inheritDoc}
     *
     * <p>流程：按 userId 取用户 → 用 {@link RawPassword} 规范化明文（非法长度即判否）→
     * 委托 {@link User#matchesPassword} 比对哈希。任一边界均返回 {@code false}，不抛业务异常。</p>
     */
    @Override
    public boolean verifyPassword(long userId, String rawPassword) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            // 安全默认：用户不存在不区分原因，统一判否（防枚举）。
            return false;
        }
        RawPassword candidate;
        try {
            candidate = RawPassword.of(rawPassword);
        } catch (InvalidCredentialException e) {
            // 提交的密码长度等不合法：直接判否，不向上抛（验证失败是预期分支）。
            return false;
        }
        return user.matchesPassword(candidate, passwordHasher);
    }
}
