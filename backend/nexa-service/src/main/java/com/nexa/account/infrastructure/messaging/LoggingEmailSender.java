package com.nexa.account.infrastructure.messaging;

import com.nexa.account.application.port.EmailSender;
import com.nexa.account.domain.vo.Email;
import com.nexa.account.domain.vo.VerificationCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 邮件发送端口 {@link EmailSender} 的日志桩实现（基础设施层）。
 *
 * <p>承载 F-1004 发验证码邮件、F-1006 发密码重置邮件。本切片<b>不真发信</b>，仅把投递动作
 * 落日志，便于本地/联调环境观察。<b>TODO 生产环境换真实邮件服务实现</b>（SMTP / SES / 第三方），
 * 应用层只依赖 {@link EmailSender} 端口，换实现不动用例签名（backend-engineer §2.3）。</p>
 *
 * <p>安全：日志桩刻意打印验证码/令牌以便联调；<b>真实实现绝不可把验证码/令牌写入日志</b>
 * （会成为凭证泄露面）。这里仅因桩不真发信、需要可观测才打印，故标注醒目 TODO。</p>
 */
@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    /** {@inheritDoc} */
    @Override
    public void sendVerificationCode(Email email, VerificationCode code) {
        // TODO 生产换真实邮件服务；真实实现绝不可打印验证码（凭证泄露面）。此桩仅联调可观测用。
        log.info("[EMAIL-STUB] send verification code to {} -> code={} (NOT actually sent; stub)",
                email.value(), code.value());
    }

    /** {@inheritDoc} */
    @Override
    public void sendPasswordResetEmail(Email email, String resetToken) {
        // TODO 生产换真实邮件服务；真实实现应只发含令牌的重置链接、且不落日志。此桩仅联调可观测用。
        log.info("[EMAIL-STUB] send password reset email to {} -> token={} (NOT actually sent; stub)",
                email.value(), resetToken);
    }
}
