package com.nexa.passkey.domain.exception;

/**
 * Passkey（WebAuthn）限界上下文领域异常基类。
 *
 * <p>passkey 域所有业务规则违反统一继承本类，携带稳定业务错误码（{@link #code()}），
 * 供接口层翻译为 HTTP 状态码 + 响应 message（对齐 openapi {@code ErrorResponse}）。
 * 与账号域 {@code DomainException} 同构（同样纯 Java RuntimeException、带稳定 code），但归属各自
 * bounded context 不跨域共享，避免上下文耦合（backend-engineer §2.5/§3.2）。</p>
 */
public abstract class PasskeyException extends RuntimeException {

    private final String code;

    /**
     * @param code    稳定业务错误码（如 {@code PASSKEY_NOT_FOUND}），供接口层映射与前端识别
     * @param message 面向开发者/可读的错误描述
     */
    protected PasskeyException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** @return 稳定业务错误码 */
    public String code() {
        return code;
    }
}
