package com.nexa.sensitiveverify.domain.exception;

/**
 * 敏感动作二次验证限界上下文领域异常基类（F-1038）。
 *
 * <p>本上下文（{@code com.nexa.sensitiveverify}）所有业务规则违反统一继承本类，携带稳定业务错误码
 * （{@link #code()}），供接口层翻译为 HTTP 状态码 + 响应 message（对齐 openapi {@code ErrorResponse}）。
 * 与账号域 {@code DomainException}、passkey 域 {@code PasskeyException} 同构（同为纯 Java
 * RuntimeException + 稳定 code），但归属各自 bounded context 不跨域共享，避免上下文耦合
 * （backend-engineer §2.5「DDD ≠ 微服务，模块化单体按 bounded context 分包」/§3.2 错误用明确类型）。</p>
 */
public abstract class SensitiveVerifyException extends RuntimeException {

    private final String code;

    /**
     * @param code    稳定业务错误码（如 {@code SENSITIVE_VERIFY_FAILED}），供接口层映射与前端识别
     * @param message 面向开发者/可读的错误描述（不含敏感凭据原文）
     */
    protected SensitiveVerifyException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** @return 稳定业务错误码 */
    public String code() {
        return code;
    }
}
