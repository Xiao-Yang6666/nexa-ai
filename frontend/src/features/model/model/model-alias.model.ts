'use client';

/**
 * features/model/model — 用户自助模型映射 C→A 视图模型 + 服务端状态 hooks。
 *
 * 客户端零泄露：target 仅平台公开模型 A，列表/候选/请求体均不含上游真实模型 B。
 * 候选源 = /api/user/self/model_aliases/candidates（公开 A 全集），用于「平台模型 A」联想。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type {
  UserModelAliasUserView,
  UserModelAliasCreateRequest,
  UserModelAliasUpdateRequest,
} from '@/shared/api';
import { ApiError } from '@/shared/api';
import {
  getModelAliases,
  getAliasCandidates,
  createModelAlias,
  updateModelAlias,
  deleteModelAlias,
} from '../api/model-alias.api';

/** 作用域：用户级 / 分组级。 */
export type AliasScope = 'user' | 'group';

/** 映射条目视图模型（零泄露白名单）。 */
export interface AliasVM {
  id: number;
  scope: AliasScope;
  /** C 客户别名 */
  alias: string;
  /** A 目标公开名（绝不含 B） */
  target: string;
  enabled: boolean;
}

/** DTO → 视图模型（显式白名单，杜绝敏感字段透传）。 */
export function toAliasVM(a: UserModelAliasUserView): AliasVM {
  return {
    id: a.id ?? 0,
    scope: a.scope_type === 'group' ? 'group' : 'user',
    alias: a.alias ?? '',
    target: a.target ?? '',
    enabled: a.enabled ?? true,
  };
}

/** 全量映射查询 hook（两作用域共存，前端按 scope 分组）。 */
export function useModelAliases() {
  return useQuery<AliasVM[], ApiError>({
    queryKey: ['model-aliases'],
    queryFn: async () => (await getModelAliases()).map(toAliasVM),
  });
}

/** 候选平台模型 A 联想 hook（公开全集，B 不可见闸）。 */
export function useAliasCandidates(keyword?: string) {
  return useQuery<string[], ApiError>({
    queryKey: ['alias-candidates', keyword ?? ''],
    queryFn: () => getAliasCandidates(keyword),
    staleTime: 5 * 60 * 1000,
  });
}

/** 新建映射 mutation。 */
export function useCreateAlias() {
  const qc = useQueryClient();
  return useMutation<UserModelAliasUserView, ApiError, UserModelAliasCreateRequest>({
    mutationFn: (payload) => createModelAlias(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['model-aliases'] }),
  });
}

/** 更新映射 mutation（改 target / 启停）。 */
export function useUpdateAlias() {
  const qc = useQueryClient();
  return useMutation<UserModelAliasUserView, ApiError, { id: number; payload: UserModelAliasUpdateRequest }>({
    mutationFn: ({ id, payload }) => updateModelAlias(id, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['model-aliases'] }),
  });
}

/** 删除映射 mutation。 */
export function useDeleteAlias() {
  const qc = useQueryClient();
  return useMutation<unknown, ApiError, number>({
    mutationFn: (id) => deleteModelAlias(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['model-aliases'] }),
  });
}
