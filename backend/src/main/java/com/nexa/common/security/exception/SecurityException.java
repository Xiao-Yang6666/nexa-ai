package com.nexa.common.security.exception;

/**
 * 安全横切领域异常基类。
 *
 * <p>{@code com.nexa.common.security} 子域（全站 HTTPS 强制 / 统一输入校验 / 防注入 / 敏感数据加密）
 * 的所有业务规则违反统一继承本类，携带稳定业务错误码（{@link #code()}），供接口层翻译成
 * HTTP 状态码 + 错误信封（对齐 openapi {@code ErrorResponse}）。</p>
 *
 * <p>设计依据：backend-engineer §3.2「领域错误用明确错误类型/错误码，不靠 panic 控流」；
 * 与业务域简单型 {@link com.nexa.common.kernel.DomainException} 同形态（code+message），但属安全横切
 * 子域、语义独立而单列，domain 层零框架依赖，纯 {@link RuntimeException} 派生。</p>
 */
public abstract class SecurityException extends RuntimeException {

    private final String code;

    /**
     * @param code    稳定业务错误码（如 {@code INSECURE_TRANSPORT}），供接口层映射与前端识别
     * @param message 面向开发者/可读的错误描述
     */
    protected SecurityException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * @param code    稳定业务错误码
     * @param message 面向开发者/可读的错误描述
     * @param cause   底层错误（保留错误链，backend-engineer §3.2 不吞错）
     */
    protected SecurityException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** @return 稳定业务错误码 */
    public String code() {
        return code;
    }
}
