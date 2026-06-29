package com.nexa.domain.model.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 自助映射不存在异常（接口层映射 404，F-6003）。
 *
 * <p>按 id 操作 UserModelAlias 但目标缺失时抛出。</p>
 */
public class UserModelAliasNotFoundException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "USER_MODEL_ALIAS_NOT_FOUND";

    /** @param id 缺失的映射 id */
    public UserModelAliasNotFoundException(long id) {
        super(CODE, "user model alias not found: id=" + id);
    }
}
