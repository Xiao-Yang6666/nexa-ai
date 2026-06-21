package com.nexa.sensitiveverify.domain.exception;

/**
 * 敏感动作二次验证未通过异常（F-1038，接口层翻译为 403 Forbidden）。
 *
 * <p>触发场景：用户提交的所有凭据（密码 / TOTP / passkey 断言）<b>无一</b>校验通过，
 * 因而不放行受保护的敏感动作。对齐 openapi {@code POST /api/verify} 的 {@code 403 ForbiddenError}
 * 响应（验证失败 → 拒绝放行）。</p>
 *
 * <p>领域规则出处：F-1038「通用敏感动作二次验证：密码 / 2FA / passkey 任一通过即放行」——
 * 反之任一都不通过即验证失败。失败原因刻意<b>不</b>区分「是哪个因子错」（防探测/枚举，安全默认），
 * 统一回「验证未通过」语义。</p>
 */
public class SensitiveActionVerificationFailedException extends SensitiveVerifyException {

    /** 稳定业务错误码。 */
    public static final String CODE = "SENSITIVE_VERIFY_FAILED";

    /**
     * 以默认中性提示构造（不泄露具体失败因子）。
     */
    public SensitiveActionVerificationFailedException() {
        super(CODE, "sensitive action verification failed");
    }

    /**
     * @param message 失败原因（中性描述，不含敏感凭据原文）
     */
    public SensitiveActionVerificationFailedException(String message) {
        super(CODE, message);
    }
}
