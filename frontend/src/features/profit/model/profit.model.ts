/**
 * features/profit/model — 利润分析看板视图模型 + React Query hook（AdminAuth）。
 *
 * 数据源（后端无单一聚合端点，前端并发组合）：
 *   - GET /api/profit/dashboard?dimension={model|channel|group} → 各维度售价/成本/利润/利润率
 *   - GET /api/data/ → 按日配额聚合（营收趋势：按日 quota→USD）
 *
 * quota（积分）→ USD：new-api 惯例 $1 = 500000 quota（与 dashboard/billing 保持一致）。
 * 管理端视图，可展示成本/利润/供应商全字段，不受客户端零泄露约束。
 */
import { useQuery } from '@tanstack/react-query';
import type { ProfitDashboardItem, QuotaDataItem } from '@/shared/api';
import { getProfitDashboard, getQuotaData } from '../api/profit.api';

export const QUOTA_PER_USD = 500_000;
const DAY = 86_400;

function quotaToUsd(quota: number): number {
  return quota / QUOTA_PER_USD;
}

/** KPI 顶行卡。 */
export interface ProfitKpiVM {
  label: string;
  val: string;
  valCls?: 'profit' | 'rateOk' | 'rateLow';
}

/** 营收/成本按日趋势点（USD）。 */
export interface RevCostPoint {
  date: string;
  rev: number;
  cost: number;
}

/** 横向柱状项（利润，USD）。 */
export interface ProfitBarItem {
  name: string;
  val: number;
}

/** 明细表行（按维度，USD）。 */
export interface ProfitRow {
  name: string;
  sell: number;
  cost: number;
  profit: number;
  rate: number; // 利润率 %
}

export interface ProfitVM {
  kpis: ProfitKpiVM[];
  trend: RevCostPoint[];
  byModelBars: ProfitBarItem[];
  byChannelBars: ProfitBarItem[];
  modelRows: ProfitRow[];
  channelRows: ProfitRow[];
  groupRows: ProfitRow[];
}

function toRow(it: ProfitDashboardItem): ProfitRow {
  const sell = quotaToUsd(it.sum_quota_sell ?? 0);
  const cost = quotaToUsd(it.sum_quota_cost ?? 0);
  const profit = quotaToUsd(it.sum_quota_profit ?? 0);
  // profit_rate 已是 profit/sell（小数）；缺失时按 sell 兜底计算
  const rate = (typeof it.profit_rate === 'number' ? it.profit_rate : sell > 0 ? profit / sell : 0) * 100;
  return { name: it.dimension_key ?? '—', sell, cost, profit, rate };
}

/** 营收趋势：按日 quota → USD（/api/data 无成本拆分，成本线留空）。 */
function toTrend(quotaData: QuotaDataItem[]): RevCostPoint[] {
  const byDate = new Map<string, number>();
  for (const it of quotaData) {
    const date = it.date ?? '';
    byDate.set(date, (byDate.get(date) ?? 0) + (it.quota ?? 0));
  }
  return Array.from(byDate.entries())
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([date, quota]) => ({ date, rev: quotaToUsd(quota), cost: 0 }));
}

/** 三维度聚合 + 按日营收组装利润看板 VM。 */
export function assembleProfit(
  models: ProfitDashboardItem[],
  channels: ProfitDashboardItem[],
  groups: ProfitDashboardItem[],
  quotaData: QuotaDataItem[],
): ProfitVM {
  const modelRows = models.map(toRow).sort((a, b) => b.profit - a.profit);
  const channelRows = channels.map(toRow).sort((a, b) => b.profit - a.profit);
  const groupRows = groups.map(toRow).sort((a, b) => b.profit - a.profit);

  // KPI：以维度合计（用 channel 维度合计，等价于全站口径）
  const totSell = channelRows.reduce((s, r) => s + r.sell, 0);
  const totCost = channelRows.reduce((s, r) => s + r.cost, 0);
  const totProfit = channelRows.reduce((s, r) => s + r.profit, 0);
  const overallRate = totSell > 0 ? (totProfit / totSell) * 100 : 0;

  const kpis: ProfitKpiVM[] = [
    { label: '总营收（售价合计）', val: `$${totSell.toFixed(2)}` },
    { label: '总成本（进货合计）', val: `$${totCost.toFixed(2)}` },
    { label: '总利润', val: `$${totProfit.toFixed(2)}`, valCls: 'profit' },
    {
      label: '整体利润率',
      val: `${overallRate.toFixed(1)}%`,
      valCls: overallRate < 20 ? 'rateLow' : 'rateOk',
    },
  ];

  const byModelBars: ProfitBarItem[] = modelRows
    .slice(0, 6)
    .map((r) => ({ name: r.name, val: Math.round(r.profit) }));
  const byChannelBars: ProfitBarItem[] = channelRows
    .slice(0, 6)
    .map((r) => ({ name: r.name, val: Math.round(r.profit) }));

  return {
    kpis,
    trend: toTrend(quotaData),
    byModelBars,
    byChannelBars,
    modelRows,
    channelRows,
    groupRows,
  };
}

/** 利润看板查询 hook（按区间天数，默认近 30 天）。 */
export function useProfitDashboard(days = 30) {
  return useQuery({
    queryKey: ['profit', 'dashboard', days],
    queryFn: async () => {
      const end = Math.floor(Date.now() / 1000);
      const start = end - days * DAY;
      const [models, channels, groups, quotaData] = await Promise.all([
        getProfitDashboard('model', start, end),
        getProfitDashboard('channel', start, end),
        getProfitDashboard('group', start, end),
        getQuotaData(start, end),
      ]);
      return assembleProfit(
        models.items ?? [],
        channels.items ?? [],
        groups.items ?? [],
        quotaData ?? [],
      );
    },
  });
}
