package com.nexa.application.account;

import com.nexa.application.account.port.EmailSender;
import com.nexa.application.account.port.VerificationCodeService;
import com.nexa.domain.account.vo.Email;
import com.nexa.domain.account.vo.VerificationCode;
import org.springframework.stereotype.Service;

/**
 * 发送注册/找回邮箱验证码用例（应用服务，F-1004）。
 *
 * <p>编排发码流程（PRD prd-account.md AC-1 R4~R6「请求发送验证码 → 生成并下发」）：
 * 构造邮箱值对象（触发格式校验）→ 经 {@link VerificationCodeService} 签发并暂存验证码 →
 * 经 {@link EmailSender} 投递。业务无聚合状态变更，故无 {@code @Transactional}。</p>
 *
 * <p>对齐 openapi.yaml {@code GET /api/verification?email=}（F-1004）。本类<b>薄</b>，
 * 仅编排两个端口（backend-engineer §2.1）；验证码生成/TTL、发信细节由 infra 实现承载。</p>
 */
@Service
public class SendVerificationCodeUseCase {

    private final VerificationCodeService verificationCodeService;
    private final EmailSender emailSender;

    /**
     * @param verificationCodeService 验证码服务端口（签发暂存）
     * @param emailSender             邮件发送端口（投递验证码）
     */
    public SendVerificationCodeUseCase(VerificationCodeService verificationCodeService,
                                       EmailSender emailSender) {
        this.verificationCodeService = verificationCodeService;
        this.emailSender = emailSender;
    }

    /**
     * 为指定邮箱发送一枚验证码。
     *
     * @param rawEmail 目标邮箱原始串（接口层透传）
     * @throws com.nexa.domain.account.exception.InvalidCredentialException 邮箱为空/超长/格式非法
     */
    public void send(String rawEmail) {
        Email email = Email.of(rawEmail); // 在领域边界校验邮箱格式，非法直接拒绝。
        VerificationCode code = verificationCodeService.issue(email);
        emailSender.sendVerificationCode(email, code);
    }
}
