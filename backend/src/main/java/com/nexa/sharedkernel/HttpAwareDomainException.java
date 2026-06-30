package com.nexa.sharedkernel;

/**
 * 携带建议 HTTP 状态码的领域异常基类——全站「需把领域错误直接映射成 HTTP 状态码」的 bounded context 共用。
 *
 * <p>领域层各 bounded context 的业务规则违反统一继承本类，携带稳定业务错误码（{@link #code()}）+
 * 建议 HTTP 状态码（{@link #httpStatus()}），供接口层 {@code XxxExceptionHandler} 直接翻译为
 * HTTP 响应（对齐 openapi 的 ErrorResponse 与各端点 400/401/403/404/429 定义）。这是<b>领域内核</b>
 * 构件（非 web 协议构件，仅承载「建议状态码」这一与具体 HTTP 框架解耦的整数语义），故置于
 * {@code shared.kernel}；纯 Java {@code RuntimeException} 派生，零框架依赖
 * （backend-engineer §3.2「领域错误用明确错误类型/错误码，不靠 panic 控流」）。</p>
 *
 * <p>收敛背景：原本 relay/task/ops/growth/log/observability/playground 等 bounded context 各自维护一份
 * 字节级同构的带 httpStatus 的 {@code DomainException}（code+httpStatus+message[+cause] 形态），共 7 个副本。
 * 本类消除该重复。仅承载 {@code code+message}（无 httpStatus）的简单型 context（如 telegram）继续继承
 * {@link DomainException}，不要为它们强加 httpStatus 语义。</p>
 */
public abstract class HttpAwareDomainException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    /**
     * @param code       稳定业务错误码（如 {@code ENDPOINT_NOT_ALLOWED}），供接口层映射与客户端识别
     * @param httpStatus 建议 HTTP 状态码（对齐 openapi responses 定义）
     * @param message    面向开发者/可读的错误描述（绝不含 token key / 上游凭证等敏感值）
     */
    protected HttpAwareDomainException(String code, int httpStatus, String message) {
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
    protected HttpAwareDomainException(String code, int httpStatus, String message, Throwable cause) {
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
