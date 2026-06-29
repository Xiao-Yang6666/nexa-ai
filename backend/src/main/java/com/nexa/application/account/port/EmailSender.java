package com.nexa.application.account.port;

import com.nexa.domain.account.vo.Email;
import com.nexa.domain.account.vo.VerificationCode;

/**
 * 邮件发送端口（应用层依赖，基础设施层实现）。
 *
 * <p>负责把验证码 / 密码重置链接投递到用户邮箱（F-1004 发注册/找回验证码、F-1006 发重置邮件）。
 * 抽象为端口以便用例不直接耦合 SMTP/第三方邮件服务；本切片提供日志桩实现（不真发信），
 * 后续 wave 换真实邮件服务实现即可，应用层签名不动（backend-engineer §2.3）。</p>
 */
public interface EmailSender {

    /**
     * 发送邮箱验证码邮件（注册/找回验证码场景）。
     *
     * @param email 收件邮箱
     * @param code  验证码
     */
    void sendVerificationCode(Email email, VerificationCode code);

    /**
     * 发送密码重置邮件（含一次性重置令牌，供用户点链接进重置页）。
     *
     * <p>仅对已注册邮箱发有效令牌；对未注册邮箱的「统一提示但不发有效令牌」防枚举策略
     * （PRD AC-3 F2-否）由调用方用例决定是否调用本方法。</p>
     *
     * @param email      收件邮箱
     * @param resetToken 一次性密码重置令牌
     */
    void sendPasswordResetEmail(Email email, String resetToken);
}
