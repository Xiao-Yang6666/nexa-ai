/**
 * features/dashboard/model — 仪表盘域视图模型。
 *
 * - 聚合 self（quota/used/request_count）+ 月统计（log stat）→ KPI 视图模型。
 * - 趋势/分布等图表数据：契约暂无聚合按日/分模型 series 端点，
 *   暂用客户端静态产品演示数据（保守；后续 S7 补端点后切真实）。
 * 客户端零泄露：仅本人维度，不读取成本/利润/上游字段。
 */
import { useQuery } from '@tanstack/react-query';
import {
  getSelfAccount,
  getSpendStat,
} from '@/features/billing/api/billing.api';
import { quotaToUsd } from '@/features/billing/model/billing.model';

/** KPI 卡视图模型。 */
export interface KpiVM {
  /** 本月调用量（请求数） */
  monthCalls: number;
  /** 本月消费 USD 文案 */
  monthSpendUsd: string;
  /** 当前余额 USD 文案 */
  balanceUsd: string;
  /** 累计请求数 */
  totalRequests: number;
}

/** 聚合 KPI 查询 hook（自取 self + 月度 stat）。 */
export function useKpi() {
  return useQuery({
    queryKey: ['dashboard', 'kpi'],
    queryFn: async () => {
      const start = Math.floor(new Date(new Date().getFullYear(), new Date().getMonth(), 1).getTime() / 1000);
      const [self, stat] = await Promise.all([getSelfAccount(), getSpendStat(start)]);
      const vm: KpiVM = {
        monthCalls: Math.round((stat?.rpm ?? 0) * 60 * 24 * new Date().getDate()),
        monthSpendUsd: quotaToUsd(stat?.quota),
        balanceUsd: quotaToUsd(self.quota),
        totalRequests: self.request_count ?? 0,
      };
      return vm;
    },
  });
}

/* ── 图表演示数据（静态；后续接真实 series 端点） ─────────────────────── */

/** 近 30 天调用量趋势（千次/日）。 */
export const TREND_30D: number[] = [
  42, 48, 45, 52, 60, 58, 64, 71, 68, 74,
  82, 79, 88, 95, 91, 98, 104, 110, 107, 118,
  124, 121, 130, 138, 135, 142, 150, 147, 156, 163,
];

/** 模型调用分布（公开模型 A 名 + 占比）；不含上游 B / 供应商。 */
export const MODEL_DIST: { name: string; val: number; col: string }[] = [
  { name: 'opus-4.8', val: 38, col: '--chart-1' },
  { name: 'gpt-4o', val: 24, col: '--chart-2' },
  { name: 'gemini-2.5-pro', val: 16, col: '--chart-4' },
  { name: 'gpt-4o-mini', val: 12, col: '--chart-5' },
  { name: '其他', val: 10, col: '--chart-6' },
];

/** 延迟分布（按公开模型 A 名）。 */
export const LATENCY_BY_MODEL: { m: string; p50: number; p95: number }[] = [
  { m: 'opus-4.8', p50: 380, p95: 920 },
  { m: 'gpt-4o', p50: 420, p95: 1040 },
  { m: 'gemini-2.5-pro', p50: 310, p95: 760 },
  { m: 'gpt-4o-mini', p50: 180, p95: 430 },
  { m: 'deepseek-v3', p50: 240, p95: 580 },
];
