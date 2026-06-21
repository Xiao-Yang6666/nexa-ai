/**
 * features/model/api — 用户自助模型映射 C→A 接口调用。
 * 路径/方法/出参对齐 openapi.yaml（DTO: UserModelAliasUserView/Create/Update），不臆造字段。
 * 客户端零泄露：target 仅平台公开名 A，绝不含上游真实模型 B。
 */
import { http } from '@/shared/api';
import type {
  UserModelAliasUserView,
  UserModelAliasCreateRequest,
  UserModelAliasUpdateRequest,
} from '@/shared/api';

/**
 * 用户自助映射列表。
 * openapi: GET /api/user/self/model_aliases → ApiResponse{ data: UserModelAliasUserView[] }
 */
export function getModelAliases(): Promise<UserModelAliasUserView[]> {
  return http.get<UserModelAliasUserView[]>('/api/user/self/model_aliases');
}

/**
 * 候选平台模型 A 全集（联想用，B 不可见闸）。
 * openapi: GET /api/user/self/model_aliases/candidates (F-6003) → ApiResponse{ data: string[] }
 */
export function getAliasCandidates(keyword?: string): Promise<string[]> {
  return http.get<string[]>('/api/user/self/model_aliases/candidates', {
    query: keyword ? { keyword } : undefined,
  });
}

/**
 * 新建映射。
 * openapi: POST /api/user/self/model_aliases (UserModelAliasCreateRequest)
 */
export function createModelAlias(
  payload: UserModelAliasCreateRequest,
): Promise<UserModelAliasUserView> {
  return http.post<UserModelAliasUserView>('/api/user/self/model_aliases', { json: payload });
}

/**
 * 更新映射（改 target / 启停）。
 * openapi: PUT /api/user/self/model_aliases/{id} (UserModelAliasUpdateRequest)
 */
export function updateModelAlias(
  id: number,
  payload: UserModelAliasUpdateRequest,
): Promise<UserModelAliasUserView> {
  return http.put<UserModelAliasUserView>(`/api/user/self/model_aliases/${id}`, { json: payload });
}

/**
 * 删除映射。
 * openapi: DELETE /api/user/self/model_aliases/{id}
 */
export function deleteModelAlias(id: number): Promise<unknown> {
  return http.delete<unknown>(`/api/user/self/model_aliases/${id}`);
}
