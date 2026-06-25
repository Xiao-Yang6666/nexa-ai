'use client';

/**
 * features/ranking/model/ranking.model — 用量排行榜视图模型 + 数据 hook。
 *
 * 数据源：后端真实接口 GET /api/rankings?period=week|month（F-4010 用量快照）。
 * PublicView 口径：只含对外名 A + 聚合用量（quota），绝不含成本/利润/上游模型 B/供应商。
 * 厂商/上下文由 model 域按名解析（modelDisplayMeta），仅用于展示补全。
 *
 * 注：本期排行榜只做「用量榜」（真实日志聚合）。综合/性价比/最快响应三榜及官方价对比
 * 涉及能力分/生成速度/竞品官方价等人工策划数据，运行时无法测得，本期暂不做（见下 TODO）。
 *
 * TODO(ranking-multidim): 若后续要恢复多维榜单，需先在后端补数据源——
 *   - 能力分 capability_score / 生成速度 speed_tps：建议作 public_models 可编辑列（管理后台维护）；
 *   - 竞品官方价 official_price：同上，或接外部价格源；
 *   再开对应 /api/rankings?board=overall|value|speed 维度或独立端点，前端按维度切换。
 *   切勿再回退到前端写死的 mock 评估常量（已于本次重构删除 ranking-data.ts）。
 */
import { useQuery } from '@tanstack/react-query';
import { ApiError } from '@/shared/api';
import { quotaToUsd, quotaUsdValue } from '@/features/billing';
import { modelDisplayMeta } from '@/features/model';
import { getRankings, type RankingPeriod } from '../api/ranking.api';

/** 排行周期（对齐契约 period 枚举）。 */
export type { RankingPeriod } from '../api/ranking.api';

/** 周期 tab 配置（顺序即展示顺序）。 */
export const PERIODS: { id: RankingPeriod; label: string; desc: string }[] = [
  { id: 'week', label: '近 7 天', desc: '最近 7 天经 Nexa 网关的真实调用用量排行。' },
  { id: 'month', label: '近 30 天', desc: '最近 30 天经 Nexa 网关的真实调用用量排行。' },
];

/** 单模型用量排行视图模型（全部为公开展示字段，零泄露）。 */
export interface RankingRowVM {
  /** 名次（1 起，按用量降序） */
  rank: number;
  /** 对外公开名 A */
  modelName: string;
  /** 厂商（按名解析，展示补全；未命中为「—」） */
  vendor: string;
  /** 上下文体量（展示补全；未命中为「—」） */
  ctx: string;
  /** 聚合用量（quota 原值，售价口径） */
  usedQuota: number;
  /** 聚合用量换算 USD 数值（用于进度条归一） */
  usedUsdValue: number;
  /** 聚合用量 USD 展示串（如 $1,234.50） */
  usedUsdLabel: string;
}

/**
 * RankingPublicView[] → 用量排行 VM 列表。
 * rank 缺省时按下标补；按名解析厂商/上下文（仅展示补全，不含任何敏感字段）。
 */
export function toRankingRowVMs(
  views: { rank?: number; public_model?: string; used_quota_or_count?: number }[],
): RankingRowVM[] {
  return views.map((v, i) => {
    const modelName = v.public_model ?? '';
    const meta = modelDisplayMeta(modelName);
    const usedQuota = typeof v.used_quota_or_count === 'number' ? v.used_quota_or_count : 0;
    return {
      rank: v.rank ?? i + 1,
      modelName,
      vendor: meta.vendor,
      ctx: meta.ctx,
      usedQuota,
      usedUsdValue: quotaUsdValue(usedQuota),
      usedUsdLabel: quotaToUsd(usedQuota),
    };
  });
}

/**
 * 用量排行榜数据 hook：拉 /api/rankings?period 并映射成零泄露 VM 列表。
 * 公开接口（匿名可访问）；React Query 管缓存/loading/error。
 */
export function useRankings(period: RankingPeriod) {
  return useQuery<RankingRowVM[], ApiError>({
    queryKey: ['rankings', period],
    queryFn: async () => toRankingRowVMs(await getRankings(period)),
    staleTime: 5 * 60 * 1000,
  });
}
