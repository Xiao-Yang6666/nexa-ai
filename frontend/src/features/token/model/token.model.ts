/**
 * features/token/model — 令牌域视图模型：API 密钥列表 + 状态 + 额度用量。
 *
 * - quota（积分）→ USD 展示换算（new-api 惯例 $1 = 500000 quota）。
 * - TokenUserView → 表格行视图模型（状态徽章 / 额度进度）。
 * - React Query hooks 管服务端状态（list / create / update / delete / 明文 key）。
 * 客户端零泄露：令牌客户视图无成本/利润/上游字段。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TokenUserView, TokenCreateRequest } from '@/shared/api';
import {
  getTokens,
  createToken,
  updateToken,
  deleteToken,
  getTokenKey,
  getUserGroups,
} from '../api/token.api';

/** quota（积分）→ USD 数值。new-api 惯例 $1 = 500000 quota。 */
export const QUOTA_PER_USD = 500_000;

/** quota → USD 数值。 */
export function quotaUsd(quota: number | undefined): number {
  return (quota ?? 0) / QUOTA_PER_USD;
}

/** 令牌状态语义（new-api：1=启用，2=禁用，3=过期，4=耗尽）。 */
export type TokenState = 'active' | 'disabled' | 'expired' | 'exhausted';

/** 令牌行视图模型。 */
export interface TokenRowVM {
  id: number;
  name: string;
  /** 脱敏 key 前缀（MaskTokenKey 已脱敏） */
  keyMasked: string;
  state: TokenState;
  stateLabel: string;
  /** 状态徽章 class + 圆点 token 变量 */
  badgeClass: string;
  dotVar: string;
  /** 已用 USD */
  usedUsd: number;
  /** 总额度 USD（unlimited 时为 null） */
  totalUsd: number | null;
  unlimited: boolean;
  /** 用量百分比（0-100；unlimited 时 0） */
  pct: number;
  /** 进度条预警色：'' | 'warn' | 'dan' */
  progTone: '' | 'warn' | 'dan';
  group: string;
  /** 创建时间文案 */
  createdAt: string;
  /** 最后使用文案 */
  accessedAt: string;
  /** 是否启用（用于禁用/启用切换语义） */
  enabled: boolean;
}

const STATE_META: Record<TokenState, { label: string; badge: string; dot: string }> = {
  active: { label: '已启用', badge: 'b-suc', dot: '--color-success' },
  disabled: { label: '已禁用', badge: 'b-neutral', dot: '--color-text-muted' },
  expired: { label: '已过期', badge: 'b-dan', dot: '--color-danger' },
  exhausted: { label: '额度耗尽', badge: 'b-warn', dot: '--color-warning' },
};

function fmtDate(ts: number | undefined): string {
  if (!ts || ts < 0) return '—';
  const d = new Date(ts * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function fmtRelative(ts: number | undefined): string {
  if (!ts || ts <= 0) return '从未使用';
  const diff = Date.now() / 1000 - ts;
  if (diff < 60) return '刚刚';
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`;
  if (diff < 86400 * 30) return `${Math.floor(diff / 86400)} 天前`;
  return fmtDate(ts);
}

/** 由 DTO 推断令牌状态。 */
function deriveState(t: TokenUserView): TokenState {
  const status = t.status ?? 1;
  if (status !== 1) {
    // status 非启用：契约未细分，按 new-api 惯例 2=禁用,3=过期,4=耗尽
    if (status === 3) return 'expired';
    if (status === 4) return 'exhausted';
    return 'disabled';
  }
  const exp = t.expired_time ?? -1;
  if (exp > 0 && exp * 1000 < Date.now()) return 'expired';
  if (!t.unlimited_quota && (t.remain_quota ?? 0) <= 0) return 'exhausted';
  return 'active';
}

/** TokenUserView → 行视图模型。 */
export function toTokenRowVM(t: TokenUserView): TokenRowVM {
  const state = deriveState(t);
  const meta = STATE_META[state];
  const unlimited = t.unlimited_quota ?? false;
  const used = quotaUsd(t.used_quota);
  const remain = quotaUsd(t.remain_quota);
  const total = unlimited ? null : used + remain;
  const pct = total && total > 0 ? Math.min(100, Math.round((used / total) * 100)) : 0;
  const progTone: '' | 'warn' | 'dan' = pct >= 90 ? 'dan' : pct >= 75 ? 'warn' : '';
  return {
    id: t.id ?? 0,
    name: t.name || '未命名密钥',
    keyMasked: t.key || 'sk-****',
    state,
    stateLabel: meta.label,
    badgeClass: meta.badge,
    dotVar: meta.dot,
    usedUsd: used,
    totalUsd: total,
    unlimited,
    pct,
    progTone,
    group: t.group || 'default',
    createdAt: fmtDate(t.created_time),
    accessedAt: fmtRelative(t.accessed_time),
    enabled: state === 'active' || state === 'exhausted',
  };
}

/** 令牌列表查询 hook。 */
export function useTokens(page = 1, pageSize = 20) {
  return useQuery({
    queryKey: ['token', 'list', page, pageSize],
    queryFn: () => getTokens(page, pageSize),
    select: (data) => ({
      rows: (data.items ?? []).map(toTokenRowVM),
      total: data.total ?? 0,
    }),
  });
}

/** 创建令牌 mutation。 */
export function useCreateToken() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: TokenCreateRequest) => createToken(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['token', 'list'] }),
  });
}

/** 启用/禁用令牌 mutation（status_only 局部更新）。 */
export function useToggleToken() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, enable }: { id: number; enable: boolean }) =>
      updateToken(id, { status_only: true, status: enable ? 1 : 2 }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['token', 'list'] }),
  });
}

/** 删除令牌 mutation。 */
export function useDeleteToken() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteToken(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['token', 'list'] }),
  });
}

/** 拉取明文 key（受控，仅在用户主动点击时调用）。 */
export function useTokenKey() {
  return useMutation({
    mutationFn: (id: number) => getTokenKey(id),
  });
}

/** 可选套餐分组项（创建 key 时绑定）：code + 展示名 + 折扣倍率。 */
export interface GroupOptionVM {
  code: string;
  name: string;
  /** 分组折扣倍率（×系数） */
  ratio: number;
}

/**
 * 可选套餐分组列表 hook（套餐制：apikey 必须绑定一个有权限且存在的分组）。
 *
 * 数据源 GET /api/user/self/model_groups（USER 鉴权）：返回公开 + 已授权私有、
 * 且启用+模型集非空的分组。后端创建 key 时也用同一可访问集合校验，杜绝孤儿分组。
 */
export function useUserGroups() {
  return useQuery({
    queryKey: ['token', 'groups'],
    queryFn: () => getUserGroups(),
    select: (data): GroupOptionVM[] =>
      (data ?? []).map((g) => ({
        code: g.code,
        name: g.name,
        ratio: Number(g.priceRatio) || 1,
      })),
  });
}
