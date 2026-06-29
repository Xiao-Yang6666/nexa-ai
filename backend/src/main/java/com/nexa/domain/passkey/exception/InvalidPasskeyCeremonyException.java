package com.nexa.domain.passkey.exception;

/**
 * WebAuthn ceremony（注册/断言）校验失败异常（F-1028/1029/1030）。
 *
 * <p>触发场景：challenge 缺失/过期/不匹配、attestation/assertion 验签失败、签名计数器回退
 * （疑似克隆，DB-SCHEMA §16 {@code cloneWarning}）、凭据已被占用等。接口层翻译为 400。</p>
 *
 * <p>领域规则出处：PRD 无密码登录（WebAuthn ceremony 两段式 begin/finish，challenge 一次性消费、
 * 签名计数器单调递增防重放/克隆）。</p>
 */
public class InvalidPasskeyCeremonyException extends PasskeyException {

    /** 稳定业务错误码。 */
    public static final String CODE = "PASSKEY_CEREMONY_INVALID";

    /**
     * @param message 失败原因（不含敏感凭据原文）
     */
    public InvalidPasskeyCeremonyException(String message) {
        super(CODE, message);
    }
}
