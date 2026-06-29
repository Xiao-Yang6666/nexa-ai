package com.nexa.domain.account.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 邮箱验证码错误或已过期异常。
 *
 * <p>对应 PRD prd-account.md AC-1 分支「验证码不匹配或已过期 → R7-否 → 拒绝创建，不落库 →
 * 验证码错误/过期态」（F-1005）。当 {@code EmailVerificationEnabled=true} 且注册请求携带的
 * 验证码缺失/不匹配/过期时由注册用例抛出。</p>
 */
public class VerificationCodeException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "VERIFICATION_CODE_INVALID";

    /**
     * 以默认面向用户的提示构造。
     */
    public VerificationCodeException() {
        super(CODE, "verification code is incorrect or expired");
    }

    /**
     * @param message 错误描述（不泄露正确验证码等敏感信息）
     */
    public VerificationCodeException(String message) {
        super(CODE, message);
    }
}
