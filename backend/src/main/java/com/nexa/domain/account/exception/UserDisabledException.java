package com.nexa.domain.account.exception;

import com.nexa.sharedkernel.DomainException;

/**
 * 账号被封禁异常。
 *
 * <p>对应 PRD prd-account.md AC-2 分支「Status≠UserStatusEnabled（被封禁）→ 拒绝登录，
 * 不建会话 → 账号已封禁拒绝态」（L3-否）。密码正确但状态非启用时抛出。</p>
 */
public class UserDisabledException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "USER_DISABLED";

    public UserDisabledException() {
        super(CODE, "user account is disabled");
    }
}
