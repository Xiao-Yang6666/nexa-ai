package com.nexa.domain.account.twofa.exception;

/**
 * 2FA 验证码（TOTP 或备份码）校验失败。
 *
 * <p>接口层翻译为 400（凭据不匹配，等同 ErrInvalidCode）。用于 enable 第二步、登录第二步、
 * 关闭校验等场景。不区分"TOTP 错"还是"备份码错"以避免给攻击者额外信息（安全默认：
 * 统一为"验证码无效"）。</p>
 */
public final class InvalidTwoFACodeException extends TwoFAException {

    /** 稳定业务错误码。 */
    public static final String CODE = "TWO_FA_INVALID_CODE";

    /**
     * @param message 错误描述
     */
    public InvalidTwoFACodeException(String message) {
        super(CODE, message);
    }
}
