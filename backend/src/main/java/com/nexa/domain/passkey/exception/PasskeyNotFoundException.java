package com.nexa.domain.passkey.exception;

/**
 * Passkey 凭据不存在异常（对齐 DB-SCHEMA §16 错误常量 {@code ErrPasskeyNotFound} /
 * {@code ErrFriendlyPasskeyNotFound}，F-1029/1030/1031/1032）。
 *
 * <p>触发场景：登录/二次验证时目标用户无已注册 passkey；本人查询/删除或管理端重置时定位不到凭据。
 * 接口层翻译为 404（资源不存在语义）。</p>
 */
public class PasskeyNotFoundException extends PasskeyException {

    /** 稳定业务错误码。 */
    public static final String CODE = "PASSKEY_NOT_FOUND";

    /**
     * @param detail 定位细节（如 {@code user_id=42} 或 {@code credentialId=...}），用于日志/排错，不回显敏感原文
     */
    public PasskeyNotFoundException(String detail) {
        super(CODE, "passkey credential not found: " + detail);
    }
}
