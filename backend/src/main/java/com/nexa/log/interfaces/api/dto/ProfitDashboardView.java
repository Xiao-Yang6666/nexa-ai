package com.nexa.log.interfaces.api.dto;

import java.util.List;

/**
 * 利润看板响应载荷视图 DTO（接口层，F-6009 GET /api/profit/dashboard）。
 *
 * <p>对齐 openapi 该端点 200 的 {@code data} 结构：{@code { items: ProfitDashboardItem[] }}。
 * 仅一层 {@code items} 包裹，便于后续扩展汇总字段（如全维度合计）而不破坏契约形态。</p>
 *
 * @param items 各维度键的利润聚合项（AdminView，按利润降序）
 */
public record ProfitDashboardView(List<ProfitDashboardItemView> items) {
}
