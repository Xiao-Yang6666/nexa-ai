/**
 * features/billing/model — 计费域视图模型：余额 / 月消费 / 充值记录 / 档位赠送。
 *
 * - quota（积分）→ USD 展示换算（new-api 惯例 $1 = 500000 quota）。
 * - UserView/LogStat → 余额面板视图模型；UserLogView (type=1) → 充值记录视图。
 * - 档位赠送规则（产品固定）：满 50 / 100 / 500 / 1000 阶梯。
 * - React Query hooks 管服务端状态。
 * 客户端零泄露：本域仅触达 self-scope 视图；不读取任何 cost/profit/上游字段。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { UserLogView, TopUpRequest } from '@/shared/api';
import {
  getSelfAccount,
  getSpendStat,
  getRechargeLogs,
  createTopUp,
} from '../api/billing.api';

/** quota（积分）→ USD 数值。new-api 惯例 $1 = 500000 quota。 */
export const QUOTA_PER_USD = 500_000;

/** quota → "$x.xx" 文案（默认 2 位小数）。 */
export function quotaToUsd(quota: number | undefined, dec = 2): string {
  const v = (quota ?? 0) / QUOTA_PER_USD;
  return `$${v.toFixed(dec)}`;
}

/** quota → USD 数值。 */
export function quotaUsdValue(quota: number | undefined): number {
  return (quota ?? 0) / QUOTA_PER_USD;
}

/** 余额面板视图模型。 */
export interface BalanceVM {
  /** 当前可用余额 USD 文案 */
  balanceUsd: string;
  /** 累计已使用 USD 文案 */
  usedUsd: string;
  /** 累计请求数 */
  requestCount: number;
  /** 邀请累计奖励 USD 文案（aff_history_quota） */
  affHistoryUsd: string;
  /** 当前分组 */
  group: string;
}

/** UserView → 余额视图模型。 */
export function toBalanceVM(view: { quota?: number; used_quota?: number; request_count?: number; aff_history_quota?: number; group?: string }): BalanceVM {
  return {
    balanceUsd: quotaToUsd(view.quota),
    usedUsd: quotaToUsd(view.used_quota),
    requestCount: view.request_count ?? 0,
    affHistoryUsd: quotaToUsd(view.aff_history_quota),
    group: view.group || 'default',
  };
}

/** 充值记录视图。 */
export interface RechargeRecordVM {
  id: number;
  /** 时间文案 */
  time: string;
  /** 充值额度 USD 文案 */
  amountUsd: string;
  /** 描述（content 字段） */
  desc: string;
}

function fmtTime(ts: number | undefined): string {
  if (!ts) return '—';
  const d = new Date(ts * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** UserLogView (type=1) → 充值记录视图模型。 */
export function toRechargeRecordVM(log: UserLogView): RechargeRecordVM {
  return {
    id: log.id ?? 0,
    time: fmtTime(log.created_at),
    amountUsd: quotaToUsd(log.quota),
    desc: log.content || '账户充值',
  };
}

/** 档位赠送规则（产品固定）。返回赠送 USD 数值。 */
export function giftFor(amount: number): number {
  if (amount >= 1000) return Math.round(amount * 0.12 * 100) / 100;
  if (amount >= 500) return Math.round(amount * 0.1 * 100) / 100;
  if (amount >= 100) return Math.round(amount * 0.05 * 100) / 100;
  if (amount >= 50) return Math.round(amount * 0.04 * 100) / 100;
  return 0;
}

/* ── React Query hooks ─────────────────────────────────────────────────── */

/** 本人账户余额查询 hook。 */
export function useBalance() {
  return useQuery({
    queryKey: ['billing', 'self'],
    queryFn: () => getSelfAccount(),
    select: toBalanceVM,
  });
}

/** 本人消费聚合查询 hook（默认本月 = 当月 1 日起）。 */
export function useMonthSpend() {
  const start = Math.floor(new Date(new Date().getFullYear(), new Date().getMonth(), 1).getTime() / 1000);
  return useQuery({
    queryKey: ['billing', 'stat', 'month'],
    queryFn: () => getSpendStat(start),
    select: (stat) => quotaToUsd(stat?.quota),
  });
}

/** 充值记录查询 hook。 */
export function useRechargeRecords(page = 1, pageSize = 20) {
  return useQuery({
    queryKey: ['billing', 'recharge', page, pageSize],
    queryFn: () => getRechargeLogs(page, pageSize),
    select: (data) => (data.items ?? []).map(toRechargeRecordVM),
  });
}

/** 充值下单 mutation。 */
export function useCreateTopUp() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: TopUpRequest) => createTopUp(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['billing'] });
    },
  });
}
