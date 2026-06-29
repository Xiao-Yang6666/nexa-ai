'use client';

/**
 * features/modelgroup/model — 模型组视图模型 + React Query hooks。
 *
 * 对齐 ModelGroupAdminView：id/name/code/base_price_ratio/models/access_policy/status/...
 * 管理端 CRUD + 启停 + 用户私有组覆盖式设置。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getPublicModels } from '@/features/model/api/model-admin.api';
import {
  getModelGroups,
  createModelGroup,
  updateModelGroup,
  updateModelGroupStatus,
  deleteModelGroup,
  getUserModelGroups,
  setUserModelGroups,
  type AccessPolicy,
  type ModelGroupView,
  type ModelGroupCreateRequest,
  type ModelGroupUpdateRequest,
} from '../api/modelgroup.api';

/** 模型组状态码。 */
export const MG_STATUS = { ENABLED: 1, DISABLED: 2 } as const;

/** 访问策略中文标签。 */
export const POLICY_LABEL: Record<AccessPolicy, string> = {
  PUBLIC: '公开',
  PRIVATE: '私有',
  AUTO_LEVEL: '按等级自动',
};

/** 模型组行视图模型。 */
export interface ModelGroupRowVM {
  id: number;
  name: string;
  code: string;
  ratio: number;
  models: string[];
  modelCount: number;
  policy: AccessPolicy;
  policyLabel: string;
  enabled: boolean;
  description?: string;
  updatedAt: string;
}

function fmtDate(ts: number | undefined | null): string {
  if (!ts || ts <= 0) return '—';
  const d = new Date(ts * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function toModelGroupRowVM(g: ModelGroupView): ModelGroupRowVM {
  return {
    id: g.id,
    name: g.name,
    code: g.code,
    ratio: g.base_price_ratio,
    models: g.models ?? [],
    modelCount: (g.models ?? []).length,
    policy: g.access_policy,
    policyLabel: POLICY_LABEL[g.access_policy] ?? g.access_policy,
    enabled: g.status === MG_STATUS.ENABLED,
    description: g.description,
    updatedAt: fmtDate(g.updated_time),
  };
}

/** 模型组列表 hook（可选按策略过滤）。 */
export function useModelGroups(accessPolicy?: AccessPolicy) {
  return useQuery({
    queryKey: ['model_groups', accessPolicy ?? 'all'],
    queryFn: () => getModelGroups(accessPolicy),
    select: (data) => (data ?? []).map(toModelGroupRowVM),
  });
}

/**
 * 候选模型名 hook（价格分组「包含模型」勾选列表的数据源）。
 *
 * 拉对外模型全集（public_models）的 public_name，供分组编辑抽屉勾选——替代手敲模型名 textarea，
 * 避免敲错/失联。一次取较大页（200）覆盖全量；按 public_name 升序稳定排序。
 */
export function useCandidateModels() {
  return useQuery({
    queryKey: ['model_groups', 'candidate-models'],
    queryFn: async () => {
      const res = await getPublicModels(1, 200);
      return (res.items ?? [])
        .map((m) => m.public_name ?? '')
        .filter((n): n is string => n.length > 0)
        .sort((a, b) => a.localeCompare(b));
    },
    staleTime: 5 * 60 * 1000,
  });
}

/** 创建模型组。 */
export function useCreateModelGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ModelGroupCreateRequest) => createModelGroup(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['model_groups'] }),
  });
}

/** 更新模型组。 */
export function useUpdateModelGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: number; req: ModelGroupUpdateRequest }) =>
      updateModelGroup(args.id, args.req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['model_groups'] }),
  });
}

/** 启用/禁用模型组。 */
export function useToggleModelGroupStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: number; status: number }) =>
      updateModelGroupStatus(args.id, args.status),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['model_groups'] }),
  });
}

/** 软删除模型组。 */
export function useDeleteModelGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteModelGroup(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['model_groups'] }),
  });
}

/** 查某用户已授权私有组 hook（enabled 控制懒加载，仅抽屉打开时拉）。 */
export function useUserModelGroups(userId: number | null) {
  return useQuery({
    queryKey: ['user_model_groups', userId],
    queryFn: () => getUserModelGroups(userId as number),
    enabled: userId != null && userId > 0,
    select: (data) => (data ?? []).map(toModelGroupRowVM),
  });
}

/** 覆盖式设置某用户私有组。 */
export function useSetUserModelGroups() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { userId: number; codes: string[] }) =>
      setUserModelGroups(args.userId, args.codes),
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ['user_model_groups', vars.userId] });
    },
  });
}
