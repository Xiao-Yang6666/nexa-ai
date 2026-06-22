/**
 * features/modelgroup/api — 灵活模型组管理域接口调用（管理端，AdminAuth）。
 *
 * 对齐后端 ModelGroupController：
 *   GET    /api/admin/model_group?access_policy=PUBLIC|PRIVATE|AUTO_LEVEL  列表（可选按策略过滤）
 *   POST   /api/admin/model_group                                          创建
 *   PUT    /api/admin/model_group/{id}                                     更新（部分更新，code 不可改）
 *   PATCH  /api/admin/model_group/{id}/status                              启用/禁用
 *   DELETE /api/admin/model_group/{id}                                     软删除
 *   GET    /api/admin/model_group/{id}/access                              授权清单
 *   POST   /api/admin/model_group/{id}/access                              授权用户/令牌
 *   DELETE /api/admin/model_group/access/{accessId}                        撤销授权
 *   GET    /api/admin/model_group/user/{userId}                            查某用户已授权私有组
 *   PUT    /api/admin/model_group/user/{userId}                            覆盖式设置某用户私有组
 */
import { http } from '@/shared/api';

/** 访问策略。 */
export type AccessPolicy = 'PUBLIC' | 'PRIVATE' | 'AUTO_LEVEL';

/** 授权主体类型。 */
export type AccessSubjectType = 'USER' | 'TOKEN';

/** 模型组管理视图（对齐 ModelGroupAdminView）。 */
export interface ModelGroupView {
  id: number;
  name: string;
  code: string;
  base_price_ratio: number;
  models: string[];
  access_policy: AccessPolicy;
  /** 1=启用 2=禁用 */
  status: number;
  description?: string;
  created_time: number;
  updated_time: number;
}

/** 授权记录视图（对齐 ModelGroupAccessView）。 */
export interface ModelGroupAccessView {
  id: number;
  model_group_id: number;
  subject_type: AccessSubjectType;
  subject_id: number;
  created_time: number;
}

/** 创建请求。 */
export interface ModelGroupCreateRequest {
  name: string;
  code: string;
  base_price_ratio?: number;
  models?: string[];
  access_policy: AccessPolicy;
  description?: string;
}

/** 更新请求（部分更新，非空才覆盖；code 不可改）。 */
export interface ModelGroupUpdateRequest {
  name?: string;
  base_price_ratio?: number;
  models?: string[];
  access_policy?: AccessPolicy;
  description?: string;
}

/** 模型组列表（可选按策略过滤）。 */
export function getModelGroups(accessPolicy?: AccessPolicy): Promise<ModelGroupView[]> {
  return http.get<ModelGroupView[]>('/api/admin/model_group', {
    query: accessPolicy ? { access_policy: accessPolicy } : undefined,
  });
}

/** 创建模型组。 */
export function createModelGroup(req: ModelGroupCreateRequest): Promise<ModelGroupView> {
  return http.post<ModelGroupView>('/api/admin/model_group', { json: req });
}

/** 更新模型组。 */
export function updateModelGroup(id: number, req: ModelGroupUpdateRequest): Promise<ModelGroupView> {
  return http.put<ModelGroupView>(`/api/admin/model_group/${id}`, { json: req });
}

/** 启用/禁用模型组（status: 1 启用 / 2 禁用）。 */
export function updateModelGroupStatus(id: number, status: number): Promise<ModelGroupView> {
  return http.patch<ModelGroupView>(`/api/admin/model_group/${id}/status`, { json: { status } });
}

/** 软删除模型组。 */
export function deleteModelGroup(id: number): Promise<void> {
  return http.delete<void>(`/api/admin/model_group/${id}`);
}

/** 某模型组的授权清单。 */
export function getModelGroupAccess(id: number): Promise<ModelGroupAccessView[]> {
  return http.get<ModelGroupAccessView[]>(`/api/admin/model_group/${id}/access`);
}

/** 授权某用户/令牌访问私有模型组。 */
export function grantModelGroupAccess(
  id: number,
  subjectType: AccessSubjectType,
  subjectId: number,
): Promise<ModelGroupAccessView> {
  return http.post<ModelGroupAccessView>(`/api/admin/model_group/${id}/access`, {
    json: { subjectType, subjectId },
  });
}

/** 撤销授权。 */
export function revokeModelGroupAccess(accessId: number): Promise<void> {
  return http.delete<void>(`/api/admin/model_group/access/${accessId}`);
}

/** 查某用户已被授权的私有模型组（用户列表/编辑回显）。 */
export function getUserModelGroups(userId: number): Promise<ModelGroupView[]> {
  return http.get<ModelGroupView[]>(`/api/admin/model_group/user/${userId}`);
}

/** 覆盖式设置某用户的私有模型组授权（codes 为最终应拥有的全部组编码；空数组=清空）。 */
export function setUserModelGroups(userId: number, codes: string[]): Promise<ModelGroupView[]> {
  return http.put<ModelGroupView[]>(`/api/admin/model_group/user/${userId}`, {
    json: { codes },
  });
}
