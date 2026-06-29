package com.nexa.domain.modelgroup.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 模型组参数非法异常（→ HTTP 400）。
 *
 * <p>名称/编码/倍率/模型列表/访问策略等字段校验失败时由聚合工厂或值对象抛出。携带稳定错误码
 * {@code MODEL_GROUP_INVALID_PARAMETER}。</p>
 */
public class InvalidModelGroupParameterException extends DomainException {

    /**
     * @param message 可读的字段非法描述（不含敏感值）
     */
    public InvalidModelGroupParameterException(String message) {
        super("MODEL_GROUP_INVALID_PARAMETER", message);
    }
}
