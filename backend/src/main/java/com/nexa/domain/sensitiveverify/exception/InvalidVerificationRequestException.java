package com.nexa.domain.sensitiveverify.exception;

/**
 * 二次验证请求非法异常（F-1038，接口层翻译为 400 Bad Request）。
 *
 * <p>触发场景：请求体未携带<b>任何</b>可识别的验证凭据（既无 password、又无 totp、又无 passkey
 * 断言）——无凭据无从验证，属请求错误而非验证失败（区别于 {@link SensitiveActionVerificationFailedException}
 * 的 403）。区分二者可让前端正确引导：400「补齐凭据」 vs 403「凭据错误，重试或换因子」。</p>
 */
public class InvalidVerificationRequestException extends SensitiveVerifyException {

    /** 稳定业务错误码。 */
    public static final String CODE = "SENSITIVE_VERIFY_BAD_REQUEST";

    /**
     * @param message 失败原因（不含敏感凭据原文）
     */
    public InvalidVerificationRequestException(String message) {
        super(CODE, message);
    }
}
