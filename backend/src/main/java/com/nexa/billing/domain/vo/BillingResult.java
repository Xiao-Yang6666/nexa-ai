package com.nexa.billing.domain.vo;

/**
 * 计费结算结果值对象 —— 单次请求的售价/成本/利润三金额（prd-billing BL-9 逐笔三金额）。
 *
 * <p>不可变、按值相等。{@link BillingCalculator} 结算后产出本对象，对应 DATA-MODEL §5 Log 的
 * 三个冗余金额列（落库便于利润看板直接聚合，避免每次重算，prd-billing BL-9 §1）：
 * <ul>
 *   <li>{@code quotaSell}（售价 = 客户实付，<b>乘 GroupRatio 折扣</b>，BL-8 最终扣费，客户可见）；</li>
 *   <li>{@code quotaCost}（成本 = tokens×CostRatio(channel,B)，<b>不乘 GroupRatio</b>，ADR-BILL-02，仅 admin/root）；</li>
 *   <li>{@code quotaProfit = quotaSell − quotaCost}（逐笔利润，可为负=亏损告警，仅 admin/root）。</li>
 * </ul></p>
 *
 * <p>{@code costMissing} 标记成本行缺失/禁用（prd-billing BL-7 成本缺失态）：此时 {@code quotaCost=0}、
 * {@code quotaProfit=quotaSell}（利润虚高，看板需告警 cost_missing）；售价照扣不阻断。</p>
 *
 * @param quotaSell   售价额度（客户实付，含分组折扣）
 * @param quotaCost   成本额度（不含折扣；成本缺失时为 ZERO）
 * @param quotaProfit 利润额度（= sell − cost，可为负）
 * @param costMissing 成本是否缺失（true 时利润虚高，看板告警）
 */
public record BillingResult(Quota quotaSell, Quota quotaCost, long quotaProfit, boolean costMissing) {

    /**
     * 工厂方法：由售价、成本（可空表示缺失）组装，自动算利润与缺失标记。
     *
     * <p>利润允许为负（svip 折扣过狠 / 成本高于折后售价 → 亏损），故用裸 {@code long} 承载
     * （{@link Quota} 不允许负值）。成本为 {@code null} 视为缺失：成本记 0、利润 = 售价（虚高，标缺失）。</p>
     *
     * @param sell 售价额度（客户实付，含折扣）
     * @param cost 成本额度；{@code null} 表示成本行缺失/禁用（BL-7 兜底）
     * @return 结算结果值对象
     */
    public static BillingResult of(Quota sell, Quota cost) {
        if (cost == null) {
            // 成本缺失兜底：quota_cost=0，利润=售价（虚高），标 cost_missing 供看板告警（BL-7 §4）。
            return new BillingResult(sell, Quota.ZERO, sell.value(), true);
        }
        long profit = sell.value() - cost.value();
        return new BillingResult(sell, cost, profit, false);
    }
}
