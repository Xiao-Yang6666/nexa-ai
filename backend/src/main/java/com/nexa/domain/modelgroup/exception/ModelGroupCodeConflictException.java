package com.nexa.domain.modelgroup.exception;

import com.nexa.common.kernel.DomainException;

/**
 * 模型组编码冲突异常（→ HTTP 409）。
 *
 * <p>创建/更新时 {@code code} 与其它存活模型组重复时由应用层抛出（{@code code} 是模型组的全局唯一
 * 业务标识，中继请求按 code 选组）。携带稳定错误码 {@code MODEL_GROUP_CODE_CONFLICT}。</p>
 */
public class ModelGroupCodeConflictException extends DomainException {

    /**
     * @param code 冲突的模型组编码
     */
    public ModelGroupCodeConflictException(String code) {
        super("MODEL_GROUP_CODE_CONFLICT", "model group code already exists: " + code);
    }
}
