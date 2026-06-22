package com.nexa.log.domain.exception;

/**
 * 日志查询域领域异常基类（日志与用量 bounded context，F-4001~F-4014 + F-6012）。
 *
 * <p>读侧查询/统计/配额按日/排行/审计写入/历史清理的业务规则违反统一继承本类，携带稳定业务
 * 错误码（{@link #code()}）+ 建议 HTTP 状态码（{@link #httpStatus()}），供接口层翻译为响应
 * （对齐 openapi ErrorResponse 与各端点 400/401/403 定义）。</p>
 *
 * <p>设计依据：backend-engineer §3.2「领域错误用明确错误类型/错误码，不靠 panic 控流」；
 * domain 层零框架依赖，本异常为纯 Java RuntimeException 派生（与 com.nexa.token /
 * com.nexa.relay 各 bounded context 的 DomainException 同构，互不耦合）。</p>
 */
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    /**
     * @param code       稳定业务错误码（如 {@code LOG_INVALID_PARAMETER}）
     * @param httpStatus 建议 HTTP 状态码（对齐 openapi responses 定义）
     * @param message    面向开发者/可读的错误描述（绝不含 token key / 上游凭证等敏感值）
     */
    protected DomainException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    /**
     * @param code       稳定业务错误码
     * @param httpStatus 建议 HTTP 状态码
     * @param message    错误描述
     * @param cause      根因（错误链保留，backend-engineer §3.2 不吞错）
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
