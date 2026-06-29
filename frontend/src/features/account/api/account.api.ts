/**
 * features/account/api — 账号域接口调用（基于 shared/api 的 http）。
 * 路径/方法/入参/出参逐字对齐 openapi.yaml，不臆造字段。
 */
import { http } from '@/shared/api';
import type {
  UserView, LoginPayload, RegisterPayload, UserAdminView, Pagination,
  BalanceTransactionView, AdminBalanceAdjustRequest,
} from '@/shared/api';

/** /api/user/ 管理端用户列表查询参数（对齐 openapi F-1008，分页）。 */
export interface UserListQuery {
  page?: number;
  page_size?: number;
}

/** /api/user/ 返回的分页结构（Pagination + items: UserAdminView[]）。 */
export interface UserAdminPage extends Pagination {
  items: UserAdminView[];
}

/**
 * 管理端用户列表（分页）。
 * openapi: GET /api/user/ (F-1008, adminAuth) → ApiResponse{ data: Pagination & { items: UserAdminView[] } }
 */
export function listUsers(query: UserListQuery = {}): Promise<UserAdminPage> {
  return http.get<UserAdminPage>('/api/user/', {
    query: { page: query.page, page_size: query.page_size },
  });
}

/**
 * 管理员给用户充值。
 * openapi: POST /api/user/{id}/credit (adminAuth) → ApiResponse{ data: number(USD) }
 */
export function creditUser(id: number, req: AdminBalanceAdjustRequest): Promise<number> {
  return http.post<number>(`/api/user/${id}/credit`, { json: req });
}

/**
 * 管理员给用户扣费（扣到 0 为止）。
 * openapi: POST /api/user/{id}/debit (adminAuth) → ApiResponse{ data: number(USD) }
 */
export function debitUser(id: number, req: AdminBalanceAdjustRequest): Promise<number> {
  return http.post<number>(`/api/user/${id}/debit`, { json: req });
}

/**
 * 用户账变流水（充值/扣费/兑换/自助）。
 * openapi: GET /api/user/{id}/balance-logs (adminAuth) → ApiResponse{ data: BalanceTransactionView[] }
 */
export function getBalanceLogs(id: number, limit = 50): Promise<BalanceTransactionView[]> {
  return http.get<BalanceTransactionView[]>(`/api/user/${id}/balance-logs`, {
    query: { limit },
  });
}

/**
 * 邮箱/用户名 + 密码登录。
 * openapi: POST /api/user/login (F-1002) → ApiResponse{ data: UserView }
 */
export function login(payload: LoginPayload): Promise<UserView> {
  return http.post<UserView>('/api/user/login', { json: payload });
}

/**
 * 邮箱密码注册。
 * openapi: POST /api/user/register (F-1001) → SuccessResponse（不下发 password/access_token）
 */
export function register(payload: RegisterPayload): Promise<unknown> {
  return http.post<unknown>('/api/user/register', { json: payload });
}

/**
 * 登出。openapi: GET /api/user/logout (F-1003)
 */
export function logout(): Promise<unknown> {
  return http.get<unknown>('/api/user/logout');
}

/**
 * 获取本人信息（含邀请统计与 setting）。
 * openapi: GET /api/user/self (F-1045) → ApiResponse{ data: UserView }
 */
export function getSelf(): Promise<UserView> {
  return http.get<UserView>('/api/user/self');
}

/**
 * 保存本人个人设置。
 * openapi: PUT /api/user/self/setting (F-1014) → SuccessResponse
 */
export function saveSetting(setting: Record<string, unknown>): Promise<unknown> {
  return http.put<unknown>('/api/user/self/setting', { json: { setting } });
}
