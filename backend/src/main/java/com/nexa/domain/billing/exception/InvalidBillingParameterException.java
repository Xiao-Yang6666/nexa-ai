package com.nexa.domain.billing.exception;

import com.nexa.common.kernel.DomainException;

/**
 * 计费参数非法异常（缺失必填 / 越界 / 格式错误 / 金额为负等）。
 *
 * <p>由值对象工厂（{@code Money.of}/{@code Quota.of}/{@code Ratio.of}）与各聚合的构造/行为方法在
 * 守护不变量时抛出，接口层 {@code BillingExceptionHandler} 翻译为 400（对齐 openapi
 * {@code BadRequestError}）。错误码 {@code BILLING_PARAM_INVALID}。</p>
 */
public class InvalidBillingParameterException extends DomainException {

    /**
     * @param message 参数非法的可读描述（不含敏感值）
     */
    public InvalidBillingParameterException(String message) {
        super("BILLING_PARAM_INVALID", message);
    }
}
