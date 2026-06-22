package com.nexa.shared.kernel;

/**
 * 领域异常基类（携带稳定业务错误码）——全站简单型 bounded context 共用。
 *
 * <p>领域层各 bounded context 的业务规则违反统一继承本类，携带稳定业务错误码（{@link #code()}），
 * 供接口层 {@code XxxExceptionHandler} 翻译成 HTTP 状态码 + 响应 message。这是<b>领域内核</b>构件
 * （非 web 协议构件），故置于 {@code shared.kernel} 而非 {@code shared.web}；纯 Java
 * {@code RuntimeException} 派生，零框架依赖（backend-engineer §3.2「领域错误用明确错误类型/错误码」）。</p>
 *
 * <p>收敛背景：原本各 bounded context 各自维护一份字节级同构的 {@code DomainException}（code+message
 * 形态），共 10 个简单型副本。本类消除该重复。<b>注意</b>：携带 {@code httpStatus} 语义的 bounded
 * context（如 relay/task/ops 等，其接口层依赖 {@code httpStatus()} 直接取状态码）<b>不</b>继承本类，
 * 仍维护各自的带 httpStatus 基类——它们承载额外语义，统一会污染本简单型契约。</p>
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    /**
     * @param code    稳定业务错误码（如 {@code MODEL_GROUP_CODE_CONFLICT}），供接口层映射与客户端识别
     * @param message 面向开发者/可读的错误描述（绝不含凭证/token 等敏感值）
     */
    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** @return 稳定业务错误码 */
    public String code() {
        return code;
    }
}
