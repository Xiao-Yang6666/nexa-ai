/**
 * features/token/api — 令牌域接口调用（API 密钥 CRUD）。
 * 路径/方法/出参逐字对齐 openapi.yaml，不臆造字段。
 * 客户端零泄露：TokenUserView 无成本/利润/供应商字段。
 */
import { http } from '@/shared/api';
import type { TokenUserView, TokenCreateRequest, Pagination } from '@/shared/api';

/** 令牌分页列表响应（对齐 openapi GET /api/token/ data 结构）。 */
export interface TokenPage extends Pagination {
  items: TokenUserView[];
}

/**
 * 获取令牌列表（分页，key 脱敏）。
 * openapi: GET /api/token/ (F-3002) → ApiResponse{ data: TokenPage }
 */
export function getTokens(page = 1, pageSize = 20): Promise<TokenPage> {
  return http.get<TokenPage>('/api/token/', { query: { p: page, page_size: pageSize } });
}

/**
 * 创建令牌。
 * openapi: POST /api/token/ (F-3001) → ApiResponse{ data: TokenUserView }
 */
export function createToken(req: TokenCreateRequest): Promise<TokenUserView> {
  return http.post<TokenUserView>('/api/token/', { json: req });
}

/**
 * 更新令牌（含 status_only）。
 * openapi: PUT /api/token/ (F-3006) → ApiResponse{ data: TokenUserView }
 */
export function updateToken(
  id: number,
  patch: Partial<TokenCreateRequest> & { status_only?: boolean; status?: number },
): Promise<TokenUserView> {
  return http.put<TokenUserView>('/api/token/', { json: { id, ...patch } });
}

/**
 * 删除单个令牌。
 * openapi: DELETE /api/token/{id} (F-3007) → SuccessResponse
 */
export function deleteToken(id: number): Promise<void> {
  return http.delete<void>(`/api/token/${id}`);
}

/**
 * 获取单个令牌明文 key（受控）。
 * openapi: POST /api/token/{id}/key (F-3004) → ApiResponse{ data: string }
 */
export function getTokenKey(id: number): Promise<string> {
  return http.post<string>(`/api/token/${id}/key`);
}

/**
 * 拉取当前用户可选套餐分组（套餐制：创建 key 必须绑定一个有权限且存在的分组）。
 * 后端 USER 端点，返回公开 + 已授权私有、且启用+模型集非空的分组。
 * GET /api/user/self/model_groups → ApiResponse{ data: UserGroupOption[] }
 */
export interface UserGroupOption {
  code: string;
  name: string;
  /** 分组售价倍率（折扣=1 口径，前端展示 ×系数） */
  priceRatio: number;
  /** 该套餐勾选的可用模型（对外名 A） */
  models: string[];
}

export function getUserGroups(): Promise<UserGroupOption[]> {
  return http.get<UserGroupOption[]>('/api/user/self/model_groups');
}
