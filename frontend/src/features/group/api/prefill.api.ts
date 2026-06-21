/**
 * features/group/api/prefill.api — 预填分组管理域接口调用（管理端，AdminAuth）。
 *
 * 对齐 openapi F-2012~F-2015 + 后端 PrefillGroupController：
 *   GET    /api/prefill_group?type=model|tag|endpoint   列表（可选按类型过滤）
 *   POST   /api/prefill_group                           创建
 *   PUT    /api/prefill_group                           更新
 *   DELETE /api/prefill_group/{id}                      软删除
 */
import { http } from '@/shared/api';

/** 预填分组类型。 */
export type PrefillGroupType = 'model' | 'tag' | 'endpoint';

/** 预填分组管理视图（对齐 PrefillGroupAdminView）。 */
export interface PrefillGroupView {
  id: number;
  name: string;
  type: PrefillGroupType;
  items: string[];
  created_time: number;
  description?: string;
}

/** 创建请求。 */
export interface PrefillGroupCreateRequest {
  name: string;
  type: PrefillGroupType;
  items?: string[];
  description?: string;
}

/** 更新请求。 */
export interface PrefillGroupUpdateRequest {
  id: number;
  name?: string;
  items?: string[];
}

/** 预填分组列表。openapi 直接返回 data: PrefillGroupView[]（非分页）。 */
export function getPrefillGroups(type?: PrefillGroupType): Promise<PrefillGroupView[]> {
  return http.get<PrefillGroupView[]>('/api/prefill_group', {
    query: type ? { type } : undefined,
  });
}

/** 创建预填分组。 */
export function createPrefillGroup(req: PrefillGroupCreateRequest): Promise<PrefillGroupView> {
  return http.post<PrefillGroupView>('/api/prefill_group', { json: req });
}

/** 更新预填分组。 */
export function updatePrefillGroup(req: PrefillGroupUpdateRequest): Promise<PrefillGroupView> {
  return http.put<PrefillGroupView>('/api/prefill_group', { json: req });
}

/** 软删除预填分组。 */
export function deletePrefillGroup(id: number): Promise<void> {
  return http.delete<void>(`/api/prefill_group/${id}`);
}
