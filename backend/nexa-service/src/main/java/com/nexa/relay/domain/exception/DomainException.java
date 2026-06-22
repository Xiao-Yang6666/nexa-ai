package com.nexa.relay.domain.exception;

/**
 * Relay 网关域领域异常基类。
 *
 * <p>Relay 网关（中继转发链路 / 协议兼容层 OpenAI⇄Anthropic / 端到端经营转发 / 视频内容代理，
 * F-3026~F-3037 + F-4046 + F-6010~F-6011）所有业务规则违反统一继承本类，携带稳定业务错误码
 * （{@link #code()}）+ 建议 HTTP 状态码（{@link #httpStatus()}），供接口层翻译成响应（对齐 openapi 的
 * ErrorResponse 与各端点 401/403/404/429 定义）。</p>
 *
 * <p>设计依据：backend-engineer §3.2「领域错误用明确错误类型/错误码，不靠 panic 控流」；
 * domain 层零框架依赖，本异常为纯 Java RuntimeException 派生（与 com.nexa.account /
 * com.nexa.channel 同构，各 bounded context 维护各自的领域异常基类，互不耦合）。</p>
 */
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    /**
     * @param code       稳定业务错误码（如 {@code TASK_NOT_FOUND}），供接口层映射与客户端识别
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
