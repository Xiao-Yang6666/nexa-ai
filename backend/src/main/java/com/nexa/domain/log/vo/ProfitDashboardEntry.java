package com.nexa.domain.log.vo;

/**
 * 利润看板聚合项值对象（F-6009 GET /api/profit/dashboard，AdminView 含成本/利润）。
 *
 * <p>由仓储按 {@link ProfitDimension} 指定列对 logs 中消费记录（Type=2）分组聚合售价/成本/利润产出。
 * 对齐 openapi {@code ProfitDashboardItem}：{@code dimension_key / sum_quota_sell / sum_quota_cost /
 * sum_quota_profit / profit_rate / cost_missing_count}。利润率 {@code profitRate = profit / sell}
 * 为派生量（在领域计算，不落 SQL），sell=0 时归零避免除零。</p>
 *
 * <p><b>可见性</b>：本项为 <b>admin/root 专属</b>（成本/利润仅管理端可见，客户视图绝不含）；model 维度的
 * dimension_key 已是对外公开名 A（绝不暴露上游模型 B/供应商），符合可见性铁律。</p>
 *
 * <p>领域规则来源：prd-billing F-6009「利润分析看板（按维度聚合）」+ BillingResult 的
 * {@code quotaProfit = quotaSell − quotaCost} 口径 + costMissing（成本缺失→利润虚高需告警）。</p>
 *
 * @param dimensionKey     维度键（model→对外名 A / channel→渠道名 / group→用户分组；空值归一为占位）
 * @param sumQuotaSell     该维度售价 quota 总和
 * @param sumQuotaCost     该维度成本 quota 总和
 * @param sumQuotaProfit   该维度利润 quota 总和（= sell − cost，可为负=亏损告警）
 * @param costMissingCount 该维度内成本缺失（quota_cost=0 且 quota_sell&gt;0）的记录条数（利润虚高告警量）
 * @param requestCount     该维度内消费记录条数（Type=2 请求数，看板量级度量）
 */
public record ProfitDashboardEntry(
        String dimensionKey,
        long sumQuotaSell,
        long sumQuotaCost,
        long sumQuotaProfit,
        long costMissingCount,
        long requestCount) {

    /**
     * 派生利润率 {@code profit / sell}（sell=0 → 0，避免除零；保留原始符号，亏损为负）。
     *
     * @return 利润率（小数，如 0.42 表示 42%）
     */
    public double profitRate() {
        if (sumQuotaSell == 0L) {
            return 0.0d;
        }
        return (double) sumQuotaProfit / (double) sumQuotaSell;
    }
}
