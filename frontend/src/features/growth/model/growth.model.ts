/**
 * features/growth/model — 增长域视图模型：签到 / 邀请。
 *
 * - quota（积分）→ USD 展示换算（new-api 惯例 $1 = 500000 quota）。
 * - 签到状态 DTO → 日历 / 连续天数 / 阶梯视图模型。
 * - React Query hooks 管服务端状态。
 * 客户端零泄露：本域仅触达 self-scope 客户视图，无成本/利润/上游字段。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { CheckinStatusView } from '@/shared/api';
import { getAffCode, getCheckinStatus, postCheckin } from '../api/growth.api';

/** quota（积分）→ USD 数值。new-api 惯例 $1 = 500000 quota。 */
export const QUOTA_PER_USD = 500_000;

/** quota → "$x.xx" 文案。 */
export function quotaToUsd(quota: number | undefined, dec = 2): string {
  const v = (quota ?? 0) / QUOTA_PER_USD;
  return `$${v.toFixed(dec)}`;
}

/** quota → USD 数值（用于计算）。 */
export function quotaUsdValue(quota: number | undefined): number {
  return (quota ?? 0) / QUOTA_PER_USD;
}

/* ── 签到视图模型 ──────────────────────────────────────────────────────── */

/** 单条签到记录视图。 */
export interface CheckinRecordVM {
  /** 日期字符串（YYYY-MM-DD） */
  date: string;
  /** 获得额度 USD 文案 */
  rewardUsd: string;
}

/** 奖励阶梯档位状态。 */
export type LadderState = 'reached' | 'near' | 'far';

/** 阶梯档位视图。 */
export interface LadderVM {
  days: number;
  rewardUsd: string;
  state: LadderState;
  /** 距达成还差天数（state≠reached 时有意义） */
  remain: number;
}

/** 日历单元状态。 */
export type CalCellState = 'done' | 'today' | 'future' | 'normal' | 'empty';

/** 日历单元视图。 */
export interface CalCellVM {
  /** 日期数字；empty 占位为 0 */
  day: number;
  state: CalCellState;
}

/** 签到页聚合视图模型。 */
export interface CheckinVM {
  /** 连续签到天数 */
  streak: number;
  /** 累计签到天数 */
  totalCheckins: number;
  /** 累计获得额度 USD 文案 */
  totalRewardUsd: string;
  /** 本月已签到天数 */
  monthChecked: number;
  /** 今日是否已签 */
  checkedToday: boolean;
  /** 签到记录（近 N 条） */
  records: CheckinRecordVM[];
  /** 日历单元（含周首空位） */
  calendar: CalCellVM[];
  /** 日历月份文案 */
  calMonthLabel: string;
  /** 已签日期集合（当月日号） */
  checkedDays: number[];
}

/** 连续签到奖励阶梯档位（产品固定规则）。 */
const LADDER_TIERS: { days: number; rewardUsd: string }[] = [
  { days: 3, rewardUsd: '$0.50' },
  { days: 7, rewardUsd: '$1.00' },
  { days: 15, rewardUsd: '$2.00' },
  { days: 30, rewardUsd: '$5.00' },
];

/** 从记录推断当月已签日号集合 + 连续天数。 */
function deriveFromRecords(view: CheckinStatusView): {
  checkedDays: number[];
  streak: number;
  monthLabel: string;
  year: number;
  monthIdx: number;
  today: number;
} {
  const records = view.records ?? [];
  const checkedDays: number[] = [];
  let year = new Date().getFullYear();
  let monthIdx = new Date().getMonth();
  for (const r of records) {
    const d = r.checkin_date ?? '';
    const parts = d.split('-');
    if (parts.length === 3) {
      year = Number(parts[0]);
      monthIdx = Number(parts[1]) - 1;
      checkedDays.push(Number(parts[2]));
    }
  }
  // 连续天数：从最新日期往回数连续（记录按日期降序假定）
  const sorted = [...checkedDays].sort((a, b) => b - a);
  let streak = 0;
  if (sorted.length) {
    streak = 1;
    for (let i = 1; i < sorted.length; i++) {
      if (sorted[i - 1] - sorted[i] === 1) streak++;
      else break;
    }
  }
  const today = sorted.length ? sorted[0] : new Date().getDate();
  const monthLabel = `${year} 年 ${monthIdx + 1} 月`;
  return { checkedDays, streak, monthLabel, year, monthIdx, today };
}

/** 构建当月日历单元（含周首空位占位）。 */
function buildCalendar(
  year: number,
  monthIdx: number,
  today: number,
  checkedDays: number[],
): CalCellVM[] {
  const checked = new Set(checkedDays);
  const first = new Date(year, monthIdx, 1).getDay(); // 0=Sun
  const days = new Date(year, monthIdx + 1, 0).getDate();
  const cells: CalCellVM[] = [];
  for (let e = 0; e < first; e++) cells.push({ day: 0, state: 'empty' });
  for (let d = 1; d <= days; d++) {
    let state: CalCellState = 'normal';
    if (d === today) state = 'today';
    else if (checked.has(d)) state = 'done';
    else if (d > today) state = 'future';
    cells.push({ day: d, state });
  }
  return cells;
}

/** 由签到状态 DTO 计算奖励阶梯档位状态。 */
export function buildLadder(streak: number): LadderVM[] {
  return LADDER_TIERS.map((t) => {
    if (streak >= t.days) return { ...t, state: 'reached' as const, remain: 0 };
    const remain = t.days - streak;
    return { ...t, state: remain <= 5 ? ('near' as const) : ('far' as const), remain };
  });
}

/** DTO → 签到页视图模型。 */
export function toCheckinVM(view: CheckinStatusView): CheckinVM {
  const { checkedDays, streak, monthLabel, year, monthIdx, today } = deriveFromRecords(view);
  const calendar = buildCalendar(year, monthIdx, today, checkedDays);
  const records: CheckinRecordVM[] = (view.records ?? []).map((r) => ({
    date: r.checkin_date ?? '',
    rewardUsd: quotaToUsd(r.quota_awarded),
  }));
  return {
    streak,
    totalCheckins: view.total_checkins ?? 0,
    totalRewardUsd: quotaToUsd(view.total_quota),
    monthChecked: view.checkin_count ?? checkedDays.length,
    checkedToday: view.checked_in_today ?? false,
    records,
    calendar,
    calMonthLabel: monthLabel,
    checkedDays,
  };
}

/** 签到状态查询 hook。 */
export function useCheckinStatus() {
  return useQuery({
    queryKey: ['checkin', 'status'],
    queryFn: () => getCheckinStatus(),
    select: toCheckinVM,
  });
}

/** 签到领取 mutation hook（成功后刷新状态）。 */
export function useCheckinMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: postCheckin,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['checkin', 'status'] });
    },
  });
}

/** 邀请码查询 hook（GET /api/user/self/aff）。 */
export function useAffCode() {
  return useQuery({
    queryKey: ['aff', 'code'],
    queryFn: () => getAffCode(),
  });
}
