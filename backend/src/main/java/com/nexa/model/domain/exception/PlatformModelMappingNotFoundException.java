package com.nexa.model.domain.exception;

/**
 * 底仓映射不存在异常（接口层映射 404，F-6002）。
 *
 * <p>按 id 操作 PlatformModelMapping 但目标缺失时抛出。</p>
 */
public class PlatformModelMappingNotFoundException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "PLATFORM_MODEL_MAPPING_NOT_FOUND";

    /** @param id 缺失的映射 id */
    public PlatformModelMappingNotFoundException(long id) {
        super(CODE, "platform model mapping not found: id=" + id);
    }
}
