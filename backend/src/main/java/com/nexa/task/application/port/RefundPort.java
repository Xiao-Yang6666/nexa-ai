package com.nexa.task.application.port;

import com.nexa.task.domain.vo.BillingContext;
import com.nexa.task.domain.vo.RefundResult;

/**
 * 退款落账端口（应用层出站端口，F-2009 退款执行）。
 *
 * <p>DDD 端口/适配器：task BC 算出「退多少」（{@link com.nexa.task.domain.model.Task#settleRefund}），
 * 但实际落账（订阅项 SubscriptionPreConsumeRecord refunded / 令牌钱包额度退回）属 billing/account BC
 * 职责。本端口在 task application 层定义，由基础设施层适配器实现（调 billing/account），避免 task BC
 * 反向依赖具体计费实现（依赖倒置）。</p>
 *
 * <p>PRD AT-4 §BillingSource 分流：subscription 走订阅退款、wallet 走令牌额度退款，由实现按
 * {@link BillingContext#billingSource} 分流。所有退款须经任务仓储 CAS 守卫包裹（调用方保证）。</p>
 */
public interface RefundPort {

    /**
     * 执行退款落账（按计费来源分流）。
     *
     * @param userId  归属用户 id
     * @param context 计费上下文（含 billing_source 决定订阅/钱包分流）
     * @param result  退款结果（类型 + 退款额度，task 域算出）
     */
    void refund(int userId, BillingContext context, RefundResult result);
}
