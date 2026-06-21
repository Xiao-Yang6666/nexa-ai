package com.nexa.account.application;

import com.nexa.account.application.port.EmailSender;
import com.nexa.account.application.port.PasswordResetTokenService;
import com.nexa.account.domain.model.User;
import com.nexa.account.domain.repository.UserRepository;
import com.nexa.account.domain.vo.Email;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 发送重置密码邮件用例（应用服务，F-1006）。
 *
 * <p>编排找回密码第一段（PRD prd-account.md AC-3 F1~F6）：构造邮箱值对象 → 判邮箱是否已注册 →
 * 已注册则签发一次性重置令牌（{@link PasswordResetTokenService}）并经 {@link EmailSender} 发重置邮件。</p>
 *
 * <p><b>防枚举铁律（PRD AC-3「非注册邮箱仍返回成功避免枚举」/ openapi 200 描述）</b>：
 * 无论邮箱是否已注册，用例都<b>静默返回成功</b>——未注册时不签发令牌、不发信，但对调用方无差别，
 * 攻击者无法据响应区分某邮箱是否注册。安全默认。</p>
 *
 * <p>只读查询无聚合状态变更，加 {@code @Transactional(readOnly = true)} 表意。</p>
 */
@Service
public class SendPasswordResetEmailUseCase {

    private final UserRepository userRepository;
    private final PasswordResetTokenService resetTokenService;
    private final EmailSender emailSender;

    /**
     * @param userRepository    用户仓储（判邮箱是否已注册）
     * @param resetTokenService 密码重置令牌服务端口（签发暂存）
     * @param emailSender       邮件发送端口（投递重置邮件）
     */
    public SendPasswordResetEmailUseCase(UserRepository userRepository,
                                         PasswordResetTokenService resetTokenService,
                                         EmailSender emailSender) {
        this.userRepository = userRepository;
        this.resetTokenService = resetTokenService;
        this.emailSender = emailSender;
    }

    /**
     * 为指定邮箱发送密码重置邮件（未注册邮箱静默成功，防枚举）。
     *
     * @param rawEmail 目标邮箱原始串（接口层透传）
     * @throws com.nexa.account.domain.exception.InvalidCredentialException 邮箱格式非法（协议级，非枚举面）
     */
    @Transactional(readOnly = true)
    public void send(String rawEmail) {
        Email email = Email.of(rawEmail);
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            // 防枚举：未注册邮箱不签发令牌、不发信，但对外无差别地静默成功返回（PRD AC-3）。
            return;
        }
        String token = resetTokenService.issue(email);
        emailSender.sendPasswordResetEmail(email, token);
    }
}
