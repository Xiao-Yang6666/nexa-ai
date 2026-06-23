/**
 * features/provider-account/api — 供应商账号管理域接口调用（管理端，AdminAuth）。
 * 路径/方法/出参对齐后端 AccountController（/api/admin/accounts），不臆造字段。
 *
 * 出参 AccountView 为管理端视图：绝不下发 credentials 原始凭证（后端已脱敏即「不下发」）。
 * 注意：供应商账号域与用户「account」域同名但无关，前端独立命名 provider-account 避免混淆。
 */
import { http } from '@/shared/api';

/** 账号-分组关联视图（group_id + 组内 priority）。 */
export interface AccountGroupView {
  group_id: number;
  priority?: number | null;
}

/**
 * 供应商账号管理视图（AccountView，credentials 绝不回显）。
 * 字段 snake_case 对齐后端 record。
 */
export interface AccountView {
  id: number;
  name: string;
  platform: string;
  type: string;
  concurrency: number;
  priority: number;
  /** 状态码：active / disabled / rate_limited */
  status: string;
  rate_limited_at?: number | null;
  rate_limit_reset_at?: number | null;
  overload_until?: number | null;
  expires_at?: number | null;
  auto_pause_on_expired: boolean;
  groups?: AccountGroupView[];
  created_time?: number | null;
  updated_time?: number | null;
}

/** 创建账号请求（name/platform/type 必填，credentials 敏感）。 */
export interface AccountCreateRequest {
  name: string;
  platform: string;
  type: string;
  credentials?: string;
  concurrency?: number;
  priority?: number;
  expires_at?: number;
  auto_pause_on_expired?: boolean;
  groups?: AccountGroupView[];
}

/** 编辑账号请求（覆盖式，credentials 空白=保留原值）。 */
export type AccountUpdateRequest = AccountCreateRequest;

/** /api/admin/accounts 列表响应（后端 AccountListView：items + total）。 */
export interface AccountListResponse {
  items: AccountView[];
  total: number;
}

/**
 * 账号列表分页。
 * GET /api/admin/accounts (adminAuth) → ApiResponse{ data: { items, total } }
 * 分页参数 p（页码）+ page_size，支持 platform 过滤。
 */
export function getAccounts(params: {
  page?: number;
  pageSize?: number;
  platform?: string;
} = {}): Promise<AccountListResponse> {
  return http.get<AccountListResponse>('/api/admin/accounts', {
    query: {
      p: params.page,
      page_size: params.pageSize,
      platform: params.platform,
    },
  });
}

/**
 * 账号详情。
 * GET /api/admin/accounts/{id} → ApiResponse{ data: AccountView }
 */
export function getAccount(id: number): Promise<AccountView> {
  return http.get<AccountView>(`/api/admin/accounts/${id}`);
}

/**
 * 创建账号。
 * POST /api/admin/accounts → ApiResponse{ data: AccountView }
 */
export function createAccount(req: AccountCreateRequest): Promise<AccountView> {
  return http.post<AccountView>('/api/admin/accounts', { json: req });
}

/**
 * 编辑账号（覆盖式）。
 * PUT /api/admin/accounts/{id} → ApiResponse{ data: AccountView }
 */
export function updateAccount(id: number, req: AccountUpdateRequest): Promise<AccountView> {
  return http.put<AccountView>(`/api/admin/accounts/${id}`, { json: req });
}

/**
 * 删除账号。
 * DELETE /api/admin/accounts/{id} → ApiResponse
 */
export function deleteAccount(id: number): Promise<void> {
  return http.delete<void>(`/api/admin/accounts/${id}`);
}

/**
 * 启停账号。
 * PATCH /api/admin/accounts/{id}/toggle?enable={bool} → ApiResponse{ data: AccountView }
 */
export function toggleAccount(id: number, enable: boolean): Promise<AccountView> {
  return http.patch<AccountView>(`/api/admin/accounts/${id}/toggle`, {
    query: { enable },
  });
}
