package com.nexa.domain.twofa.exception;

/**
 * 2FA 校验因连续失败被临时锁定。
 *
 * <p>接口层翻译为 429（Too Many Requests，限流语义）。领域规则：连续失败达阈值后锁定一段时间，
 * 防 TOTP/备份码暴力枚举（安全默认，对齐 DB-SCHEMA §14 failed_attempts / locked_until）。
 * 锁定期内任何校验直接拒绝，不消耗 authenticator 时间窗。</p>
 */
public final class TwoFALockedException extends TwoFAException {

    /** 稳定业务错误码。 */
    public static final String CODE = "TWO_FA_LOCKED";

    /**
     * @param message 错误描述
     */
    public TwoFALockedException(String message) {
        super(CODE, message);
    }
}
