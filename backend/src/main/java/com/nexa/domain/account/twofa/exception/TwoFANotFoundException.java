package com.nexa.domain.account.twofa.exception;

/**
 * 2FA 配置不存在（用户从未发起 setup / 未启用 / 已关闭后被删除）。
 *
 * <p>接口层翻译为 404。用于本人查询、关闭、生成备份码、登录第二步等需要既有 2FA 配置的场景
 * （对齐 DB-SCHEMA §14 two_fas 软删除语义：删除后视为不存在）。</p>
 */
public final class TwoFANotFoundException extends TwoFAException {

    /** 稳定业务错误码。 */
    public static final String CODE = "TWO_FA_NOT_FOUND";

    /**
     * @param message 错误描述
     */
    public TwoFANotFoundException(String message) {
        super(CODE, message);
    }
}
