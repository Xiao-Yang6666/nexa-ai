package com.nexa.account.domain.exception;

/**
 * 用户不存在异常（管理端按 id 操作目标用户时定位失败）。
 *
 * <p>领域规则来源：PRD prd-account.md AC-10 §2 前置条件「目标用户存在于 User 表」；
 * 管理端管理状态 / 更新资料 / 角色变更（F-1010~F-1012）按 {@code id} 定位目标用户，
 * 命中失败抛本异常。接口层翻译为 404。</p>
 */
public class UserNotFoundException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "USER_NOT_FOUND";

    /**
     * @param id 未命中的目标用户 id
     */
    public UserNotFoundException(long id) {
        super(CODE, "user not found: id=" + id);
    }
}
