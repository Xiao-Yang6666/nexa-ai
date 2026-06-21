package com.nexa.prefill.domain.exception;

/**
 * 预填分组域领域异常基类（模块十五，F-2012~F-2015）。
 *
 * <p>预填分组（模型组 / 标签组 / 端点组的下拉预填配置）所有业务规则违反统一继承本类，携带稳定
 * 业务错误码（{@link #code()}），供接口层翻译成 HTTP 状态码 + 响应 message（对齐 openapi 的
 * ErrorResponse）。</p>
 *
 * <p>设计依据：backend-engineer §3.2「领域错误用明确错误类型/错误码，不靠 panic 控流」；
 * domain 层零框架依赖，本异常为纯 Java RuntimeException 派生（与 com.nexa.account /
 * com.nexa.billing / com.nexa.token 同构，各 bounded context 维护各自的领域异常基类，
 * 互不耦合）。</p>
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    /**
     * @param code    稳定业务错误码（如 {@code PREFILL_GROUP_NAME_CONFLICT}），供接口层映射与前端识别
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
