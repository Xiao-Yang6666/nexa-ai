package com.nexa.ops.domain.exception;

/**
 * 运营与运维域（ops bounded context，F-4015~F-4037）领域异常基类。
 *
 * <p>本域涵盖系统初始化、全站选项配置、性能监控运维、公开内容/状态、支付合规确认等能力。
 * 业务规则违反（重复初始化 / 选项值非法 / 主题值非法 / 限流分组格式非法 / 合规声明未确认 /
 * 日志清理参数非法 等）统一继承本类，携带稳定业务错误码（{@link #code()}）+ 建议 HTTP
 * 状态码（{@link #httpStatus()}），由接口层 {@code OpsExceptionHandler} 集中翻译为
 * openapi {@code ErrorResponse}。</p>
 *
 * <p>设计依据：backend-engineer §3.2（错误用明确类型 + wrap + 不吞错）；domain 层零框架依赖，
 * 纯 Java {@link RuntimeException} 派生，与账号域 / token 域等其他 BC 的 DomainException 同构、
 * 互不耦合（各 BC 自持异常基类，避免跨上下文耦合）。</p>
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
     * @param cause      根因（错误链保留，不吞错）
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
