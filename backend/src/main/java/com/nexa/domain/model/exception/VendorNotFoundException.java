package com.nexa.domain.model.exception;

import com.nexa.domain.kernel.DomainException;

/**
 * 供应商元数据不存在异常（接口层映射 404）。
 *
 * <p>按 id 删除供应商（F-3018）但目标缺失时抛出。</p>
 */
public class VendorNotFoundException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "VENDOR_NOT_FOUND";

    /**
     * @param id 缺失的供应商 id
     */
    public VendorNotFoundException(long id) {
        super(CODE, "vendor meta not found: id=" + id);
    }
}
