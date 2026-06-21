'use client';

/**
 * features/account/model — 管理端用户列表视图模型 + React Query hook。
 *
 * DTO（UserAdminView）→ 表格视图模型（角色/状态/分组/额度格式化）。
 * 管理端视图：UserAdminView 本就是 adminAuth 全字段视图，无客户端零泄露约束
 * （零泄露铁律只约束 self-scope 客户视图；管理后台展示全链）。
 */
import { useQuery } from '@tanstack/react-query';
import type { UserAdminView } from '@/shared/api';
import { listUsers, type UserListQuery } from '../api/account.api';

/** quota（积分）→ USD 数值。new-api 惯例 $1 = 500000 quota。 */
export const QUOTA_PER_USD = 500_000;

/** quota → 美元数值（不带 $ 符号，便于进度计算）。 */
export function quotaToUsdNum(quota: number | undefined): number {
  return (quota ?? 0) / QUOTA_PER_USD;
}

/** role 编码 → 语义。1=common 10=admin 100=root（openapi UserView.role 注释）。 */
export type UserRole = 'root' | 'admin' | 'common';
export function roleOf(role: number | undefined): UserRole {
  if (role === undefined) return 'common';
  if (role >= 100) return 'root';
  if (role >= 10) return 'admin';
  return 'common';
}

/** status：1=启用，其它=禁用（openapi 注释）。 */
export function statusOf(status: number | undefined): 'on' | 'ban' {
  return status === 1 ? 'on' : 'ban';
}

/** 用户表格行视图模型。 */
export interface UserRowVM {
  id: number;
  username: string;
  displayName: string;
  email: string;
  role: UserRole;
  status: 'on' | 'ban';
  group: string;
  /** 总额度（USD 数值） */
  quotaUsd: number;
  /** 已用额度（USD 数值） */
  usedUsd: number;
  requestCount: number;
  affCount: number;
  inviterId: number;
  /** 注册时间文案（YYYY-MM-DD），created_at 为秒级时间戳；为 0 时回退末次登录 */
  registered: string;
}

function fmtDate(ts: number | undefined): string {
  if (!ts) return '—';
  const d = new Date(ts * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** UserAdminView → 行视图模型。 */
export function toUserRowVM(u: UserAdminView): UserRowVM {
  // created_at 可能为 0（历史数据未回填），回退到 last_login_at 以避免显示 1970
  const regTs = u.created_at && u.created_at > 0 ? u.created_at : u.last_login_at;
  return {
    id: u.id ?? 0,
    username: u.username ?? '',
    displayName: u.display_name ?? u.username ?? '',
    email: u.email ?? '',
    role: roleOf(u.role),
    status: statusOf(u.status),
    group: u.group || 'default',
    quotaUsd: quotaToUsdNum(u.quota),
    usedUsd: quotaToUsdNum(u.used_quota),
    requestCount: u.request_count ?? 0,
    affCount: u.aff_count ?? 0,
    inviterId: u.inviter_id ?? 0,
    registered: fmtDate(regTs),
  };
}

/** 管理端用户列表查询 hook（GET /api/user/）。 */
export function useAdminUsers(query: UserListQuery) {
  return useQuery({
    queryKey: ['account', 'admin', 'users', query],
    queryFn: () => listUsers(query),
    select: (page) => ({
      rows: page.items.map(toUserRowVM),
      total: page.total ?? 0,
      page: page.page ?? 1,
      pageSize: page.page_size ?? query.page_size ?? 20,
    }),
  });
}
