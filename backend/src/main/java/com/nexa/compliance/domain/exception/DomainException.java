package com.nexa.compliance.domain.exception;

/**
 * 合规子域领域异常基类。
 *
 * <p>合规子域（数据分级 / prompt 留存 / 数据驻地 / 合规分组选渠 / 账号注销 / 同意闸门，
 * F-5016~F-5021）所有业务规则违反统一继承本类，携带稳定业务错误码（{@link #code()}），
 * 供接口层翻译为 HTTP 状态码 + 响应 message（沿用 account BC 的 {@code DomainException} 风格，
 * backend-engineer §3.2「领域错误用明确错误类型/错误码，不靠 panic 控流」）。</p>
 *
 * <p>domain 层零框架依赖，本异常为纯 Java RuntimeException 派生，可纯 JUnit 单测。</p>
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    /**
     * @param code    稳定业务错误码（如 {@code CONSENT_REQUIRED}），供接口层映射与前端识别
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
