/**
 * features/dashboard/api — 管理后台全局概览数据（AdminAuth）。
 * 后端无单一聚合端点，前端组合多个管理接口拼出概览：
 *   - GET /api/data/                 按日配额聚合（趋势 + 模型分布 + 今日总量）
 *   - GET /api/profit/dashboard      利润看板按 channel 维度（Top 渠道）
 * 路径/方法/出参逐字对齐 openapi.yaml，不臆造字段。
 */
import { http } from '@/shared/api';
import type { QuotaDataItem, ProfitDashboardItem } from '@/shared/api';

/**
 * 管理端按日配额聚合（F-4007）。
 * openapi: GET /api/data/ → ApiResponse{ data: QuotaDataItem[] }
 */
export function getQuotaData(start?: number, end?: number): Promise<QuotaDataItem[]> {
  return http.get<QuotaDataItem[]>('/api/data/', {
    query: { start_timestamp: start, end_timestamp: end },
  });
}

/** /api/profit/dashboard 响应（items 包络）。 */
export interface ProfitDashboardResponse {
  items: ProfitDashboardItem[];
}

/**
 * 利润看板按维度聚合（F-6009）。
 * openapi: GET /api/profit/dashboard?dimension=channel → ApiResponse{ data: { items } }
 */
export function getProfitDashboard(
  dimension: 'model' | 'channel' | 'group' = 'channel',
  start?: number,
  end?: number,
): Promise<ProfitDashboardResponse> {
  return http.get<ProfitDashboardResponse>('/api/profit/dashboard', {
    query: { dimension, start_timestamp: start, end_timestamp: end },
  });
}
