package com.nexa.modelgroup.domain.exception;

/**
 * 模型组域领域异常基类。
 *
 * <p>灵活模型组管理（供应商模型 → 模型组 → 倍率/可用模型/访问策略）所有业务规则违反统一继承本类，
 * 携带稳定业务错误码（{@link #code()}），供接口层翻译成 HTTP 状态码 + 响应 message。</p>
 *
 * <p>设计依据：backend-engineer §3.2「领域错误用明确错误类型/错误码，不靠 panic 控流」；
 * domain 层零框架依赖，本异常为纯 Java RuntimeException 派生（与 com.nexa.prefill /
 * com.nexa.token 同构，各 bounded context 维护各自的领域异常基类，互不耦合）。</p>
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    /**
     * @param code    稳定业务错误码（如 {@code MODEL_GROUP_CODE_CONFLICT}），供接口层映射与前端识别
     * @param message 面向开发者/可读的错误描述
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
