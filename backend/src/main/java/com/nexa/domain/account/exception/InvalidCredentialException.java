package com.nexa.domain.account.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 凭证/输入非法异常。
 *
 * <p>用于值对象构造校验失败（用户名/邮箱/密码格式或长度非法）以及登录时
 * 账号或密码不正确（PRD prd-account.md AC-2「账号或密码错误态」L2-否）。</p>
 *
 * <p>登录失败刻意不区分"用户不存在"与"密码错误"，统一返回本异常，避免账号枚举攻击。</p>
 */
public class InvalidCredentialException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "INVALID_CREDENTIAL";

    /**
     * @param message 错误描述（不要泄露"用户是否存在"等可被枚举利用的信息）
     */
    public InvalidCredentialException(String message) {
        super(CODE, message);
    }
}
