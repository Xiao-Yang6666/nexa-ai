package com.nexa.interfaces.log.api.dto;

import com.nexa.domain.log.vo.ProfitDashboardEntry;

/**
 * 利润看板项视图 DTO（接口层，F-6009 GET /api/profit/dashboard，AdminView）。
 *
 * <p>对齐 openapi components.schemas.ProfitDashboardItem：{@code dimension_key / sum_quota_sell /
 * sum_quota_cost / sum_quota_profit / profit_rate / cost_missing_count}。字段名经全局 Jackson
 * SNAKE_CASE 序列化为下划线命名（{@code sumQuotaSell}→{@code sum_quota_sell} 等）。</p>
 *
 * <p><b>可见性</b>：本视图为 <b>admin/root 专属</b>，含成本/利润；model 维度的 dimension_key 已是对外
 * 公开名 A，绝不暴露上游模型 B/供应商（可见性铁律）。客户视图绝不返回本结构。</p>
 *
 * @param dimensionKey     维度键（model→对外名 A / channel→渠道名 / group→用户分组）
 * @param sumQuotaSell     售价 quota 总和
 * @param sumQuotaCost     成本 quota 总和
 * @param sumQuotaProfit   利润 quota 总和（= sell − cost，可为负）
 * @param profitRate       利润率（profit / sell，sell=0→0）
 * @param costMissingCount 成本缺失记录条数（利润虚高告警量）
 * @param requestCount     消费记录条数（Type=2 请求数，看板量级度量）
 */
public record ProfitDashboardItemView(
        String dimensionKey,
        long sumQuotaSell,
        long sumQuotaCost,
        long sumQuotaProfit,
        double profitRate,
        long costMissingCount,
        long requestCount) {

    /**
     * 从领域利润聚合项构造视图（利润率派生量取领域计算结果）。
     *
     * @param entry 利润看板聚合项值对象
     * @return 利润看板项视图 DTO
     */
    public static ProfitDashboardItemView from(ProfitDashboardEntry entry) {
        return new ProfitDashboardItemView(
                entry.dimensionKey(),
                entry.sumQuotaSell(),
                entry.sumQuotaCost(),
                entry.sumQuotaProfit(),
                entry.profitRate(),
                entry.costMissingCount(),
                entry.requestCount());
    }
}
