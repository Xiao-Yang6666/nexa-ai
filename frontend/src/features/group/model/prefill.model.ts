'use client';

/**
 * features/group/model — 预填分组视图模型 + React Query hooks。
 *
 * 对齐 PrefillGroupAdminView：id/name/type/items/created_time。
 * 三类型 model | tag | endpoint 独立分页展示。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getPrefillGroups,
  createPrefillGroup,
  updatePrefillGroup,
  deletePrefillGroup,
  type PrefillGroupType,
  type PrefillGroupView,
  type PrefillGroupCreateRequest,
  type PrefillGroupUpdateRequest,
} from '../api/prefill.api';

/** 预填分组行视图模型。 */
export interface PrefillRowVM {
  id: number;
  name: string;
  type: PrefillGroupType;
  items: string[];
  memberCount: number;
  createdAt: string;
  description?: string;
}

function fmtDate(ts: number | undefined | null): string {
  if (!ts || ts <= 0) return '—';
  const d = new Date(ts * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function toPrefillRowVM(g: PrefillGroupView): PrefillRowVM {
  return {
    id: g.id,
    name: g.name,
    type: g.type,
    items: g.items ?? [],
    memberCount: (g.items ?? []).length,
    createdAt: fmtDate(g.created_time),
    description: g.description,
  };
}

/** 预填分组列表 hook（一次拉全量，前端按 type 过滤，无需分页）。 */
export function usePrefillGroups(type?: PrefillGroupType) {
  return useQuery({
    queryKey: ['prefill_groups', type ?? 'all'],
    queryFn: () => getPrefillGroups(type),
    select: (data) => (data ?? []).map(toPrefillRowVM),
  });
}

/** 创建预填分组。 */
export function useCreatePrefillGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: PrefillGroupCreateRequest) => createPrefillGroup(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['prefill_groups'] }),
  });
}

/** 更新预填分组。 */
export function useUpdatePrefillGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: PrefillGroupUpdateRequest) => updatePrefillGroup(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['prefill_groups'] }),
  });
}

/** 软删除预填分组。 */
export function useDeletePrefillGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deletePrefillGroup(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['prefill_groups'] }),
  });
}
