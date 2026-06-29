package com.nexa.account.application;

import com.nexa.account.application.port.PasswordResetTokenService;
import com.nexa.account.domain.exception.InvalidResetTokenException;
import com.nexa.account.domain.model.User;
import com.nexa.account.domain.repository.UserRepository;
import com.nexa.account.domain.vo.Email;
import com.nexa.account.domain.vo.PasswordHasher;
import com.nexa.account.domain.vo.RawPassword;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 提交重置新密码用例（应用服务，F-1007）。
 *
 * <p>编排找回密码第二段的事务边界（PRD prd-account.md AC-3 F7「令牌有效且未过期 → 更新 Password，
 * 旧哈希失效」）：构造值对象 → 校验并消费一次性重置令牌（{@link PasswordResetTokenService}）→
 * 据邮箱定位账号 → 调聚合根充血方法 {@link User#resetPassword} 改密 → 持久化。</p>
 *
 * <p>分层职责：令牌有效性/时效校验是带状态的 IO，属应用层端口职责（不进聚合）；聚合只负责
 * 「把密码换成新哈希」这一不变量动作（backend-engineer §2.1 充血、§2.3 端口）。</p>
 *
 * <p>令牌无效/过期 → {@link InvalidResetTokenException}（接口层翻译为 400）。令牌一次性消费防重放。</p>
 */
@Service
public class ResetPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordResetTokenService resetTokenService;
    private final PasswordHasher passwordHasher;

    /**
     * @param userRepository    用户仓储（据邮箱定位账号 + 保存改密）
     * @param resetTokenService 密码重置令牌服务端口（校验消费）
     * @param passwordHasher    密码哈希器（domain 端口，infra 实现注入）
     */
    public ResetPasswordUseCase(UserRepository userRepository,
                                PasswordResetTokenService resetTokenService,
                                PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.resetTokenService = resetTokenService;
        this.passwordHasher = passwordHasher;
    }

    /**
     * 执行重置密码。
     *
     * @param command 重置命令（接口层翻译后的入参）
     * @throws InvalidResetTokenException 令牌缺失/不匹配/过期，或令牌邮箱与提交邮箱不符（PRD AC-3 F7-否）
     * @throws com.nexa.account.domain.exception.InvalidCredentialException 邮箱/新密码格式非法
     */
    @Transactional
    public void reset(ResetPasswordCommand command) {
        // 构造值对象：邮箱格式、新密码长度在领域边界即校验，非法直接拒绝。
        Email email = Email.of(command.email());
        RawPassword newPassword = RawPassword.of(command.newPassword());

        // F7：校验并一次性消费令牌（双因子绑定邮箱）；失败即拒绝、不改密、防重放。
        Optional<Email> verified = resetTokenService.verifyAndConsume(email, command.token());
        if (verified.isEmpty()) {
            throw new InvalidResetTokenException();
        }

        // 据邮箱定位账号。令牌已消费的前提下，账号理应存在；防御式处理找不到的边界（仍归令牌无效语义）。
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidResetTokenException::new);

        // 充血聚合：重置场景不验旧密码（身份已由令牌校验），聚合只负责换新哈希、旧哈希失效。
        user.resetPassword(newPassword, passwordHasher);
        userRepository.save(user);
    }
}
