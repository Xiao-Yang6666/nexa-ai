package com.nexa.account.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 注册功能被系统开关关闭异常。
 *
 * <p>对应 PRD prd-account.md AC-1 前置条件「RegisterEnabled=true」与分支
 * 「RegisterEnabled=false → 拒绝进入注册 → 注册已关闭态」（R1-否，系统开关 §15）。</p>
 */
public class RegisterDisabledException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "REGISTER_DISABLED";

    public RegisterDisabledException() {
        super(CODE, "user registration is currently disabled");
    }
}
