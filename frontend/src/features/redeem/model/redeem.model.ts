/**
 * features/redeem/model — 兑换码域视图模型 + React Query hooks。
 *
 * - quota（积分）→ USD 展示换算（new-api 惯例 $1 = 500000 quota）。
 * - RedemptionAdminView → 列表视图模型；状态由后端 status 码 + expired_time 派生。
 * - 后端状态码：1=未使用 / 2=已使用 / 3=已禁用；"已过期" 非独立状态码，
 *   而是 status=未使用 且 expired_time>0 且已过当前时刻 时由前端派生。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { RedemptionAdminView, RedemptionCreateRequest } from '@/shared/api';
import { getRedemptions, generateRedemptions } from '../api/redeem.api';

/** quota（积分）→ USD。new-api 惯例 $1 = 500000 quota。 */
export const QUOTA_PER_USD = 500_000;

/** 派生后的兑换码状态。 */
export type RedeemStatus = 'unused' | 'used' | 'expired' | 'disabled';

/** 兑换码列表行视图模型。 */
export interface RedeemRowVM {
  id: number;
  /** 兑换码明文（管理端可见，后端已确认无客户泄露约束） */
  code: string;
  /** 面额 USD 数值 */
  amtUsd: number;
  /** 派生状态 */
  st: RedeemStatus;
  /** 核销人 user id 文案（未核销为 "—"） */
  user: string;
  /** 创建时间文案 */
  ct: string;
  /** 过期时间文案（0=不过期 → "永久"） */
  exp: string;
  /** 批次/名称 */
  batch: string;
}

function fmtTime(ts: number | undefined | null): string {
  if (!ts) return '—';
  const d = new Date(ts * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** 由后端 status 码 + expired_time 派生展示状态。 */
function deriveStatus(view: RedemptionAdminView): RedeemStatus {
  const status = view.status ?? 1;
  if (status === 2) return 'used';
  if (status === 3) return 'disabled';
  // status=1（未使用）：检查是否已过期（expired_time>0 且早于现在）
  const exp = view.expired_time ?? 0;
  if (exp > 0 && exp * 1000 < Date.now()) return 'expired';
  return 'unused';
}

/** RedemptionAdminView → 列表行视图模型。 */
export function toRedeemRowVM(view: RedemptionAdminView): RedeemRowVM {
  const exp = view.expired_time ?? 0;
  return {
    id: view.id ?? 0,
    code: view.key ?? '',
    amtUsd: (view.quota ?? 0) / QUOTA_PER_USD,
    st: deriveStatus(view),
    user: view.used_user_id ? String(view.used_user_id) : '—',
    ct: fmtTime(view.created_time),
    exp: exp > 0 ? fmtTime(exp) : '永久',
    batch: view.name || '—',
  };
}

/* ── React Query hooks ─────────────────────────────────────────────────── */

/** 兑换码分页列表查询 hook。返回 { rows, total }。 */
export function useRedemptions(page = 1, pageSize = 20) {
  return useQuery({
    queryKey: ['redemption', 'list', page, pageSize],
    queryFn: () => getRedemptions(page, pageSize),
    select: (data) => ({
      rows: (data.items ?? []).map(toRedeemRowVM),
      total: data.total ?? 0,
    }),
  });
}

/** 生成兑换码 mutation。成功后刷新列表。返回明文 key 列表。 */
export function useGenerateRedemptions() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: RedemptionCreateRequest) => generateRedemptions(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['redemption'] });
    },
  });
}

/** USD 面额 → quota（积分）。生成兑换码入参用。 */
export function usdToQuota(usd: number): number {
  return Math.round(usd * QUOTA_PER_USD);
}

/** 有效期天数 → 过期 epoch 秒（0=不过期）。 */
export function daysToExpiry(days: number): number {
  if (!days || days <= 0) return 0;
  return Math.floor(Date.now() / 1000) + days * 86_400;
}
