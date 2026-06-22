package com.nexa.modelgroup.domain.exception;

/**
 * 模型组不存在异常（→ HTTP 404）。
 *
 * <p>按 id 更新/删除/授权时未命中存活模型组时由应用层抛出。携带稳定错误码
 * {@code MODEL_GROUP_NOT_FOUND}。</p>
 */
public class ModelGroupNotFoundException extends DomainException {

    /**
     * @param id 未命中的模型组主键
     */
    public ModelGroupNotFoundException(long id) {
        super("MODEL_GROUP_NOT_FOUND", "model group not found: " + id);
    }
}
