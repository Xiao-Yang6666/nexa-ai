package com.nexa.domain.billing.exception;

import com.nexa.common.kernel.DomainException;

/**
 * 兑换码已被使用异常（重复兑换守卫）。
 *
 * <p>prd-billing BL-4 §4「码状态=已使用 → 拒绝，不重复入账」。一次性兑换码的核心守卫，
 * 配合事务内置已用保证并发下仅一次入账成功（AC「同一有效码并发提交两次仅一次成功」）。
 * 接口层翻译为 400。错误码 {@code REDEMPTION_ALREADY_USED}。</p>
 */
public class RedemptionAlreadyUsedException extends DomainException {

    /**
     * @param message 已使用提示
     */
    public RedemptionAlreadyUsedException(String message) {
        super("REDEMPTION_ALREADY_USED", message);
    }
}
