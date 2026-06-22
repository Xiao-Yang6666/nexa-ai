/**
 * features/profit/api — 利润分析域接口调用（AdminAuth）。
 * 路径/方法/出参逐字对齐 openapi.yaml，不臆造字段。
 *
 *   - GET /api/profit/dashboard?dimension={model|channel|group}  按维度聚合售价/成本/利润（F-6009）
 *   - GET /api/data/                                             按日配额聚合（F-4007，用于营收趋势）
 */
import { http } from '@/shared/api';
import type { ProfitDashboardItem, QuotaDataItem } from '@/shared/api';

/** /api/profit/dashboard 响应（items 包络）。 */
export interface ProfitDashboardResponse {
  items: ProfitDashboardItem[];
}

/**
 * 利润看板按维度聚合（F-6009）。
 * openapi: GET /api/profit/dashboard?dimension=… → ApiResponse{ data: { items } }
 */
export function getProfitDashboard(
  dimension: 'model' | 'channel' | 'group',
  start?: number,
  end?: number,
): Promise<ProfitDashboardResponse> {
  return http.get<ProfitDashboardResponse>('/api/profit/dashboard', {
    query: { dimension, start_timestamp: start, end_timestamp: end },
  });
}

/**
 * 管理端按日配额聚合（F-4007，用于营收趋势）。
 * openapi: GET /api/data/ → ApiResponse{ data: QuotaDataItem[] }
 */
export function getQuotaData(start?: number, end?: number): Promise<QuotaDataItem[]> {
  return http.get<QuotaDataItem[]>('/api/data/', {
    query: { start_timestamp: start, end_timestamp: end },
  });
}
