package com.nexa.domain.relay.vo;

/**
 * 双价记账结果值对象（RL-7 第⑥⑦⑨步产出，配额整数口径）。
 *
 * <p>领域规则：quota_sell 客户可见；quota_cost/quota_profit 仅 admin/root。
 * {@code costMissing} 标记 (channel,B) 无成本行（Other 写 cost_missing，看板可筛）。</p>
 *
 * @param quotaSell   本笔售价（客户可见）
 * @param quotaCost   本笔成本（仅 admin/root，缺失时 0）
 * @param quotaProfit 本笔利润 = sell − cost（可为负=亏损告警）
 * @param costMissing 成本行是否缺失
 */
public record BillingResult(int quotaSell, int quotaCost, int quotaProfit, boolean costMissing) {

    /** 是否亏损（profit<0，看板告警态）。 */
    public boolean isLoss() {
        return quotaProfit < 0;
    }
}
