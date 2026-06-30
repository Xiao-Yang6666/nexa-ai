package com.nexa.domain.billing.exception;

import com.nexa.sharedkernel.DomainException;

/**
 * 兑换码已过期异常。
 *
 * <p>prd-billing BL-4 §4「码已过期（ExpiredTime 已到且非 0）→ 拒绝兑换」。接口层翻译为 400。
 * 错误码 {@code REDEMPTION_EXPIRED}。</p>
 */
public class RedemptionExpiredException extends DomainException {

    /**
     * @param message 过期提示
     */
    public RedemptionExpiredException(String message) {
        super("REDEMPTION_EXPIRED", message);
    }
}
