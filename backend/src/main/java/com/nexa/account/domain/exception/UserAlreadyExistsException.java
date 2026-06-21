package com.nexa.account.domain.exception;

/**
 * 用户名已存在异常。
 *
 * <p>对应 PRD prd-account.md AC-1 分支「用户名已存在 → 拒绝创建 → 提示 MsgUserExists」（R9-是）。
 * 注册用例在落库前查重命中时抛出。</p>
 */
public class UserAlreadyExistsException extends DomainException {

    /** 稳定业务错误码（对齐 new-api MsgUserExists 语义）。 */
    public static final String CODE = "USER_EXISTS";

    /**
     * @param username 冲突的用户名（用于日志定位，接口层不必原样回显）
     */
    public UserAlreadyExistsException(String username) {
        super(CODE, "username already exists: " + username);
    }
}
