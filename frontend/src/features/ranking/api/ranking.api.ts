/**
 * features/ranking/api — 用量排行榜接口调用（基于 shared/api 的 http）。
 * 路径/方法/出参逐字对齐 openapi.yaml GET /api/rankings（F-4010）。
 */
import { http } from '@/shared/api';
import type { RankingPublicView } from '@/shared/api';

/** 排行周期：week|month（对齐契约 period 枚举，缺省 week）。 */
export type RankingPeriod = 'week' | 'month';

/**
 * 用量排行榜快照。
 * openapi: GET /api/rankings?period=week|month（security:[sessionAuth|匿名]）→
 * ApiResponse{ data: RankingPublicView[] }。PublicView 已裁掉成本/利润/上游模型 B/供应商——客户端零泄露。
 */
export function getRankings(period: RankingPeriod): Promise<RankingPublicView[]> {
  return http.get<RankingPublicView[]>('/api/rankings', { query: { period } });
}
