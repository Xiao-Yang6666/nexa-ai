package com.nexa.account.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 密码重置令牌无效或已过期异常。
 *
 * <p>对应 PRD prd-account.md AC-3 分支「令牌无效/过期 → F7-否 → 拒绝重置，不改密码 →
 * 令牌无效/过期态」（F-1007）。提交重置新密码时若 {@code token} 缺失/不匹配/过期，
 * 由重置密码用例抛出。</p>
 */
public class InvalidResetTokenException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "RESET_TOKEN_INVALID";

    /**
     * 以默认面向用户的提示构造。
     */
    public InvalidResetTokenException() {
        super(CODE, "password reset token is invalid or expired");
    }
}
