/**
 * features/dashboard/model/admin-dashboard — 管理后台全局概览视图模型 + hook（AdminAuth）。
 *
 * 区别于本目录 dashboard.model（用户 self-scope KPI）：本文件是 admin 全站维度，
 * 含成本/利润，仅管理后台 AdminDashboardPage 使用。
 *
 * 后端无单一聚合接口，hook 并发拉取两源（按日配额 / 利润看板）并在前端组装：
 *   - KPI、近 N 天趋势、模型分布、Top 渠道。
 * quota（积分）→ USD：new-api 惯例 $1 = 500000 quota。
 */
import { useQuery } from '@tanstack/react-query';
import type { QuotaDataItem, ProfitDashboardItem } from '@/shared/api';
import { getQuotaData, getProfitDashboard } from '../api/dashboard.api';

export const QUOTA_PER_USD = 500_000;
const DAY = 86_400;

export interface KpiCardVM {
  label: string;
  val: string;
}
export interface TrendPoint {
  date: string;
  count: number;
}
export interface ModelDistItem {
  name: string;
  val: number; // 占比 %
}
export interface TopChannelItem {
  name: string;
  sellUsd: number;
}
export interface AdminDashboardVM {
  kpis: KpiCardVM[];
  trend: TrendPoint[];
  modelDist: ModelDistItem[];
  topChannels: TopChannelItem[];
}

function quotaToUsd(quota: number): number {
  return quota / QUOTA_PER_USD;
}

/** 由两源数据组装概览 VM。 */
export function assembleAdminDashboard(
  quotaData: QuotaDataItem[],
  profit: ProfitDashboardItem[],
): AdminDashboardVM {
  const byDate = new Map<string, number>();
  const byModel = new Map<string, number>();
  let totalCount = 0;
  let totalQuota = 0;
  for (const it of quotaData) {
    const date = it.date ?? '';
    const count = it.count ?? 0;
    byDate.set(date, (byDate.get(date) ?? 0) + count);
    if (it.model_name) byModel.set(it.model_name, (byModel.get(it.model_name) ?? 0) + count);
    totalCount += count;
    totalQuota += it.quota ?? 0;
  }
  const trend: TrendPoint[] = Array.from(byDate.entries())
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([date, count]) => ({ date, count }));
  const todayCount = trend.length ? trend[trend.length - 1].count : 0;

  const modelSorted = Array.from(byModel.entries()).sort((a, b) => b[1] - a[1]);
  const top5 = modelSorted.slice(0, 5);
  const restSum = modelSorted.slice(5).reduce((s, [, v]) => s + v, 0);
  const modelTotal = totalCount || 1;
  const modelDist: ModelDistItem[] = top5.map(([name, v]) => ({
    name,
    val: Math.round((v / modelTotal) * 100),
  }));
  if (restSum > 0) modelDist.push({ name: '其他', val: Math.round((restSum / modelTotal) * 100) });

  const topChannels: TopChannelItem[] = profit
    .slice()
    .sort((a, b) => (b.sum_quota_sell ?? 0) - (a.sum_quota_sell ?? 0))
    .slice(0, 6)
    .map((p) => ({ name: p.dimension_key ?? '—', sellUsd: quotaToUsd(p.sum_quota_sell ?? 0) }));

  const totalSellUsd = profit.reduce((s, p) => s + quotaToUsd(p.sum_quota_sell ?? 0), 0);

  const kpis: KpiCardVM[] = [
    { label: '今日请求量', val: todayCount.toLocaleString('en-US') },
    { label: '区间总请求', val: totalCount.toLocaleString('en-US') },
    { label: '区间消费额', val: `$${quotaToUsd(totalQuota).toFixed(2)}` },
    { label: '区间售出额', val: `$${totalSellUsd.toFixed(2)}` },
  ];

  return { kpis, trend, modelDist, topChannels };
}

/** 管理后台全局概览查询 hook（默认近 30 天）。 */
export function useAdminDashboard(days = 30) {
  const end = Math.floor(Date.now() / 1000);
  const start = end - days * DAY;
  return useQuery({
    queryKey: ['admin-dashboard', 'overview', days],
    queryFn: async () => {
      const [quotaData, profit] = await Promise.all([
        getQuotaData(start, end),
        getProfitDashboard('channel', start, end),
      ]);
      return assembleAdminDashboard(quotaData ?? [], profit.items ?? []);
    },
  });
}
