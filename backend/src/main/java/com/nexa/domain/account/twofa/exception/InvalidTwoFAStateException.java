package com.nexa.domain.account.twofa.exception;

/**
 * 2FA 状态机/字段非法（如重复启用、字段越界、未 setup 即 enable、已锁定仍尝试校验）。
 *
 * <p>接口层翻译为 400。承载领域不变量违反（充血聚合自校验抛出），不静默吞错
 * （backend-engineer §3.2）。</p>
 */
public final class InvalidTwoFAStateException extends TwoFAException {

    /** 稳定业务错误码。 */
    public static final String CODE = "TWO_FA_INVALID_STATE";

    /**
     * @param message 错误描述
     */
    public InvalidTwoFAStateException(String message) {
        super(CODE, message);
    }
}
