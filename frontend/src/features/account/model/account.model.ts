'use client';

/**
 * features/account/model — 账号域客户端状态 + 服务端状态 hooks。
 *
 * 服务端状态用 React Query（useMutation 包登录/注册）。
 * 视图模型转换层在此兜底「客户端零泄露」：即便后端误返 cost/profit/上游模型，也不向客户端暴露。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { UserView, LoginPayload, RegisterPayload, UserSetting } from '@/shared/api';
import { ApiError } from '@/shared/api';
import { getSelf, login, register, saveSetting } from '../api/account.api';

/**
 * 角色编码（与后端 domain/vo/Role 对齐：数值大小即权限高低）。
 * 1=普通用户，10=管理员，100=超级管理员。护栏与菜单可见性都以此为准。
 */
export const ROLE = {
  COMMON: 1,
  ADMIN: 10,
  ROOT: 100,
} as const;

/** 角色编码 → 中文文案（顶栏/标签展示用）。 */
export function roleLabel(role: number): string {
  if (role >= ROLE.ROOT) return '超级管理员';
  if (role >= ROLE.ADMIN) return '管理员';
  return '普通用户';
}

/** 是否具备管理后台访问权（≥admin）。登录分流 / 路由守卫的统一判定。 */
export function isAdminRole(role: number): boolean {
  return role >= ROLE.ADMIN;
}

/** 是否为超级管理员（root 专属菜单/操作的判定）。 */
export function isRootRole(role: number): boolean {
  return role >= ROLE.ROOT;
}

/**
 * 客户端可见的用户视图（零泄露白名单）。
 * 显式列举允许字段——成本(quota_cost)/利润(quota_profit)/上游模型 B/供应商一律不进此结构。
 */
export interface AccountVM {
  id: number;
  username: string;
  displayName: string;
  email: string;
  role: number;
  /** 分组（计费折扣维度，非供应商） */
  group: string;
  /** 总额度（用户自身额度，非成本） */
  quota: number;
  /** 已用额度 */
  usedQuota: number;
  requestCount: number;
  affCode: string;
  /** 累计邀请人数 */
  affCount: number;
  /** 邀请累计获得额度（积分） */
  affQuota: number;
  /** 历史邀请额度（积分） */
  affHistoryQuota: number;
  /** 个人设置（self 端点下发；客户视图） */
  setting: UserSetting;
}

/**
 * DTO → 视图模型。只挑白名单字段，杜绝把后端可能多带的敏感字段透传到 UI。
 */
export function toAccountVM(u: UserView): AccountVM {
  return {
    id: u.id ?? 0,
    username: u.username ?? '',
    displayName: u.display_name ?? u.username ?? '',
    email: u.email ?? '',
    role: u.role ?? 1,
    group: u.group ?? 'default',
    quota: u.quota ?? 0,
    usedQuota: u.used_quota ?? 0,
    requestCount: u.request_count ?? 0,
    affCode: u.aff_code ?? '',
    affCount: u.aff_count ?? 0,
    affQuota: u.aff_quota ?? 0,
    affHistoryQuota: u.aff_history_quota ?? 0,
    setting: (u.setting ?? {}) as UserSetting,
  };
}

/** 登录 mutation：成功后返回裁剪过的 AccountVM。 */
export function useLogin() {
  return useMutation<AccountVM, ApiError, LoginPayload>({
    mutationFn: async (payload) => toAccountVM(await login(payload)),
  });
}

/** 注册 mutation。 */
export function useRegister() {
  return useMutation<unknown, ApiError, RegisterPayload>({
    mutationFn: (payload) => register(payload),
  });
}

/** 本人信息查询 hook（GET /api/user/self）→ 裁剪后的 AccountVM。 */
export function useSelf() {
  return useQuery({
    queryKey: ['account', 'self'],
    queryFn: getSelf,
    select: toAccountVM,
  });
}

/** 保存个人设置 mutation（PUT /api/user/self/setting）；成功后刷新 self。 */
export function useSaveSetting() {
  const qc = useQueryClient();
  return useMutation<unknown, ApiError, Record<string, unknown>>({
    mutationFn: (setting) => saveSetting(setting),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['account', 'self'] });
    },
  });
}
