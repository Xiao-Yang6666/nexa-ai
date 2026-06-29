package com.nexa.domain.modelgroup.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 模型组（按 code）不存在异常（→ HTTP 404）。
 *
 * <p>按 code 解析模型组（如覆盖式设置用户授权时校验目标 code 有效）未命中时抛出，message 含 code
 * 便于前端定位是哪个组无效。携带稳定错误码 {@code MODEL_GROUP_NOT_FOUND}。</p>
 */
public class ModelGroupCodeNotFoundException extends DomainException {

    /**
     * @param code 未命中的模型组编码
     */
    public ModelGroupCodeNotFoundException(String code) {
        super("MODEL_GROUP_NOT_FOUND", "model group not found by code: " + code);
    }
}
