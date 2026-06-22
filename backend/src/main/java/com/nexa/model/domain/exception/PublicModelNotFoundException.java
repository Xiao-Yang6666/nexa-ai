package com.nexa.model.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 对外模型不存在异常（接口层映射 404，F-6001）。
 *
 * <p>按 id 删除或更新对外模型，但目标缺失时抛出。</p>
 */
public class PublicModelNotFoundException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "PUBLIC_MODEL_NOT_FOUND";

    /** @param id 缺失的对外模型 id */
    public PublicModelNotFoundException(long id) {
        super(CODE, "public model not found: id=" + id);
    }
}
