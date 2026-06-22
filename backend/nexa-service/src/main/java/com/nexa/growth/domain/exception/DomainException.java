package com.nexa.growth.domain.exception;

/**
 * 增长子域（签到 + 邀请返利分销）领域异常基类。
 *
 * <p>对齐 relay/account/task/prefill 各 bounded context 独立基类模式（每个 context 各持一份基类，
 * 不跨 context 复用异常类型，避免耦合）。携带稳定业务错误码 {@link #code()} 与建议 HTTP 状态码
 * {@link #httpStatus()}，供接口层在 {@code GrowthExceptionHandler} 集中翻译为 openapi
 * {@code ErrorResponse}（backend-engineer §3.2 领域错误用明确错误类型/错误码，不靠 panic 控流）。</p>
 *
 * <p>domain 层零框架依赖：本异常为纯 Java {@link RuntimeException} 派生，可在纯 JUnit 单测中断言。</p>
 */
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    /**
     * @param code       稳定业务错误码（如 {@code CHECKIN_DISABLED}）
     * @param httpStatus 接口层建议使用的 HTTP 状态码
     * @param message    面向开发者/可读的错误描述
     */
    protected DomainException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    /**
     * @param code       稳定业务错误码
     * @param httpStatus 接口层建议使用的 HTTP 状态码
     * @param message    错误描述
     * @param cause      根因（保留错误链，不吞错）
     */
    protected DomainException(String code, int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    /** @return 稳定业务错误码 */
    public String code() {
        return code;
    }

    /** @return 接口层建议使用的 HTTP 状态码 */
    public int httpStatus() {
        return httpStatus;
    }
}
