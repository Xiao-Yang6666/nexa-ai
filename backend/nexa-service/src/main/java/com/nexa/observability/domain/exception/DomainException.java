package com.nexa.observability.domain.exception;

/**
 * Observability 域领域异常基类（指标/告警/追踪 bounded context，F-5010~F-5012）。
 *
 * <p>可观测域的业务规则违反（指标维度非法 / 告警渠道配置缺失 / SLO 阈值非法）统一继承本类，携带稳定
 * 业务错误码（{@link #code()}）+ 建议 HTTP 状态码（{@link #httpStatus()}）。本域绝大多数能力为横切基础设施
 * （RED 指标导出 / 告警编排 / trace_id 贯穿），异常主要用于配置校验与领域不变量守护，而非对外端点错误。</p>
 *
 * <p>设计依据：backend-engineer §3.2；domain 层零框架依赖，纯 Java RuntimeException 派生，与其他 BC 的
 * DomainException 同构、互不耦合。</p>
 */
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    /**
     * @param code       稳定业务错误码
     * @param httpStatus 建议 HTTP 状态码
     * @param message    错误描述（不含敏感值）
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
     * @param cause      根因（错误链保留）
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
