package com.nexa.domain.model.exception;

import com.nexa.sharedkernel.DomainException;

/**
 * 模型元数据不存在异常（接口层映射 404）。
 *
 * <p>按 id 操作（删除 F-3017 / 更新 F-3016）但目标模型缺失时抛出。</p>
 */
public class ModelMetaNotFoundException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "MODEL_META_NOT_FOUND";

    /**
     * @param id 缺失的模型 id
     */
    public ModelMetaNotFoundException(long id) {
        super(CODE, "model meta not found: id=" + id);
    }
}
