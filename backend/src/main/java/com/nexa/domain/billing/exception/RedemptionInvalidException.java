package com.nexa.domain.billing.exception;

import com.nexa.sharedkernel.DomainException;

/**
 * 兑换码无效异常（不存在 / 格式非法）。
 *
 * <p>prd-billing BL-4 §4「码不存在/格式错 → 拒绝兑换」。接口层翻译为 400（对齐 openapi
 * {@code BadRequestError}）。错误码 {@code REDEMPTION_INVALID}。</p>
 */
public class RedemptionInvalidException extends DomainException {

    /**
     * @param message 无效原因（不回显原始码，避免枚举探测）
     */
    public RedemptionInvalidException(String message) {
        super("REDEMPTION_INVALID", message);
    }
}
