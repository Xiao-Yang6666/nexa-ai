package com.nexa.domain.playground.exception;

import com.nexa.common.kernel.HttpAwareDomainException;

/**
 * Playground 请求体非法异常（F-4038，→400）。
 *
 * <p>站内试用请求缺少必填字段（如 {@code model} 空、{@code messages} 空）时抛出。
 * 领域规则来源：openapi {@code OpenAIChatCompletionRequest} schema 必填项 + prd Playground
 * 入参校验。message 不回显客户原始正文（避免日志/响应放大），仅给字段级稳定提示。</p>
 */
public final class InvalidPlaygroundRequestException extends HttpAwareDomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "PLAYGROUND_INVALID_REQUEST";

    /**
     * @param message 字段级错误提示（不含客户正文）
     */
    public InvalidPlaygroundRequestException(String message) {
        super(CODE, 400, message);
    }
}
