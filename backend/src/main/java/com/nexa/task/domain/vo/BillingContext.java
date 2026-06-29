package com.nexa.task.domain.vo;

/**
 * 计费上下文值对象（PRD AT-4 PrivateData.BillingContext，退款/差额结算依据）。
 *
 * <p>领域规则 F-2009：按次计费（{@code perCallBilling=true}）终态<b>跳过</b>差额结算；
 * 按量任务失败时按 {@link BillingSource} 退回预扣额度。包含预扣额度与实际 token 用于重算。
 * 不可变值对象。</p>
 *
 * @param perCallBilling  是否按次计费（true=跳过差额结算）
 * @param billingSource   计费来源（subscription/wallet）
 * @param preConsumedQuota 预扣配额（整数额度单位）
 * @param actualTokens    实际 token 消耗（可空，SUCCESS 态才有）
 */
public record BillingContext(
        boolean perCallBilling,
        BillingSource billingSource,
        long preConsumedQuota,
        Long actualTokens
) {

    /**
     * 计费来源枚举（PRD AT-4 §BillingSource 分流）。
     */
    public enum BillingSource {
        /** 订阅项（走 SubscriptionPreConsumeRecord refunded）。 */
        SUBSCRIPTION,
        /** 令牌钱包（额度退回）。 */
        WALLET,
        /** 未知（兜底）。 */
        UNKNOWN;

        public static BillingSource fromWire(String wire) {
            if (wire == null) return UNKNOWN;
            try {
                return valueOf(wire.toUpperCase());
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }

        public String toWire() {
            return this.name().toLowerCase();
        }
    }

    /**
     * 创建计费上下文（任务提交时填充）。
     *
     * @param perCallBilling   按次计费标志
     * @param billingSource    计费来源
     * @param preConsumedQuota 预扣配额
     * @return 计费上下文
     */
    public static BillingContext of(boolean perCallBilling, BillingSource billingSource, long preConsumedQuota) {
        return new BillingContext(perCallBilling, billingSource, preConsumedQuota, null);
    }

    /**
     * 任务成功时更新实际 token 消耗（用于差额结算）。
     *
     * @param actualTokens 实际 token 数
     * @return 新的计费上下文（不可变）
     */
    public BillingContext withActualTokens(long actualTokens) {
        return new BillingContext(this.perCallBilling, this.billingSource, this.preConsumedQuota, actualTokens);
    }
}
