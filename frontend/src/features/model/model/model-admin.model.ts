/**
 * features/model/model/model-admin.model — 模型/供应商管理端视图模型 + React Query hooks。
 *
 * 服务于 ModelsAdminPage 三 Tab：对外模型 / 模型元数据 / 供应商元数据。
 * DTO→VM 在此收敛；组件只消费 VM，不直接碰 snake_case 契约字段。
 *
 * 客户视图铁律：本域为管理端能力，仅 admin 可见——这些 VM 仅在
 * 管理后台（AdminShell）使用，不流向任何客户 self-scope 视图。
 */
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type {
  PublicModelAdminView,
  PublicModelCreateRequest,
  PublicModelUpdateRequest,
  VendorAdminView,
} from '@/shared/api';
import { getModelGroups, updateModelGroup } from '@/features/modelgroup/api/modelgroup.api';
import {
  getPublicModels,
  createPublicModel,
  updatePublicModel,
  deletePublicModel,
  getModelMetas,
  getVendors,
} from '../api/model-admin.api';

/* ── 模型状态派生（底层元数据上架/下架/预发布）─────────────────────────── */

export type ModelState = 'on' | 'off' | 'pre';

/** 由后端 status 码派生展示状态。1=上架 / 0或2=下架 / 3=预发布（按 new-api 习惯，未知回落下架）。 */
function deriveModelState(status: number | undefined): ModelState {
  if (status === 1) return 'on';
  if (status === 3) return 'pre';
  return 'off';
}

/* ── 供应商元数据 ─────────────────────────────────────────────────────── */

export type VendorState = 'on' | 'off';
export interface VendorVM {
  id: number;
  nm: string;
  icon: string;
  st: VendorState;
}
export function toVendorVM(view: VendorAdminView): VendorVM {
  return {
    id: view.id ?? 0,
    nm: view.name ?? '',
    icon: view.icon || '',
    st: view.status === 1 ? 'on' : 'off',
  };
}
/** 供应商元数据 Tab 查询。 */
export function useVendors() {
  return useQuery({
    queryKey: ['model-admin', 'vendors'],
    queryFn: async () => {
      const res = await getVendors(1, 200);
      return (res.items ?? []).map(toVendorVM);
    },
  });
}

/* ── 对外模型写操作 ───────────────────────────────────────────────────── */

/** 创建对外模型 mutation。 */
export function useCreatePublicModel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: PublicModelCreateRequest) => createPublicModel(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['model-admin', 'public-models'] }),
  });
}

/** 更新对外模型 mutation（含上下架切换）。 */
export function useUpdatePublicModel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: PublicModelUpdateRequest) => updatePublicModel(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['model-admin', 'public-models'] }),
  });
}

/** 删除对外模型 mutation。 */
export function useDeletePublicModel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deletePublicModel(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['model-admin', 'public-models'] }),
  });
}

/** 用于 invalidate 全模型管理域缓存。 */
export function useInvalidateModelAdmin() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: ['model-admin'] });
}

/* ── 统一模型视图（以底层真实模型为主体，合并广场上架状态 + 所在价格分组）──────── */

/** 统一模型行视图：一行 = 一个底层真实模型，附其广场上架状态与所在价格分组。 */
export interface UnifiedModelVM {
  /** 模型名（model_metas.model_name，对外名 A 默认取此值） */
  name: string;
  /** 供应商名 */
  vendor: string;
  /** 底层元数据状态（上架/下架/预发布——指上游可用性，非广场） */
  metaState: ModelState;
  /** 标签 */
  tags: string[];
  /** 是否已上架到广场（存在对应 public_models 记录） */
  onSquare: boolean;
  /** 已上架时的 public_model id（编辑/下架用） */
  publicModelId?: number;
  /** 已上架时是否启用（enabled） */
  enabled: boolean;
  /** 基准价倍率（已上架才有） */
  basePriceRatio?: number;
  /** 展示名（已上架才有） */
  displayName?: string;
  /** 描述（已上架才有） */
  description?: string;
  /** 所在价格分组（按模型名匹配 model_groups.models[]，忽略大小写） */
  groups: { id: number; name: string; ratio: number }[];
}

/** 价格分组精简项（抽屉勾选用）。 */
export interface GroupOptionVM {
  id: number;
  name: string;
  code: string;
  ratio: number;
  /** 该模型当前是否已在此组 */
  joined: boolean;
}

/**
 * 统一模型查询：model_metas（左表，全部真实模型）⟕ public_models（哪些已上架）
 * ⟕ model_groups（反查所在组）。以模型名为连接键（忽略大小写）。
 */
export function useUnifiedModels() {
  return useQuery({
    queryKey: ['model-admin', 'unified'],
    queryFn: async () => {
      const [metasRes, vendorsRes, pmRes, groups] = await Promise.all([
        getModelMetas(1, 200),
        getVendors(1, 200),
        getPublicModels(1, 200),
        getModelGroups(),
      ]);
      const vendorName = new Map<number, string>();
      (vendorsRes.items ?? []).forEach((v) => {
        if (v.id != null) vendorName.set(v.id, v.name ?? `#${v.id}`);
      });
      // public_models 按 public_name（小写）索引，供 join。
      const pubByName = new Map<string, PublicModelAdminView>();
      (pmRes.items ?? []).forEach((p) => {
        if (p.public_name) pubByName.set(p.public_name.toLowerCase(), p);
      });
      const groupsOf = (name: string) => {
        const lower = name.toLowerCase();
        return groups
          .filter((g) => (g.models ?? []).some((m) => m.toLowerCase() === lower))
          .map((g) => ({ id: g.id, name: g.name, ratio: g.base_price_ratio }));
      };
      return (metasRes.items ?? []).map((m): UnifiedModelVM => {
        const name = m.model_name ?? '';
        const pub = pubByName.get(name.toLowerCase());
        return {
          name,
          vendor: m.vendor_id ? (vendorName.get(m.vendor_id) ?? `#${m.vendor_id}`) : '—',
          metaState: deriveModelState(m.status),
          tags: (m.tags || '').split(',').map((t) => t.trim()).filter(Boolean),
          onSquare: pub != null,
          publicModelId: pub?.id ?? undefined,
          enabled: pub?.enabled ?? false,
          basePriceRatio: pub?.base_price_ratio != null ? Number(pub.base_price_ratio) : undefined,
          displayName: pub?.display_name || undefined,
          description: pub?.description || undefined,
          groups: groupsOf(name),
        };
      });
    },
  });
}

/** 全部价格分组（抽屉勾选数据源），标记某模型是否已在各组。 */
export function useGroupOptions(modelName: string | null) {
  return useQuery({
    queryKey: ['model-admin', 'group-options', modelName],
    queryFn: async (): Promise<GroupOptionVM[]> => {
      const groups = await getModelGroups();
      const lower = (modelName ?? '').toLowerCase();
      return groups.map((g) => ({
        id: g.id,
        name: g.name,
        code: g.code,
        ratio: g.base_price_ratio,
        joined: (g.models ?? []).some((m) => m.toLowerCase() === lower),
      }));
    },
    enabled: modelName != null,
  });
}

/**
 * 同步某模型在各价格分组的归属（覆盖式 diff）。
 * targetGroupIds = 该模型最终应属于的分组 id 集合；与各组当前 models[] diff 后增删。
 */
async function syncModelGroups(modelName: string, targetGroupIds: number[]): Promise<void> {
  const groups = await getModelGroups();
  const lower = modelName.toLowerCase();
  const target = new Set(targetGroupIds);
  for (const g of groups) {
    const models = g.models ?? [];
    const has = models.some((m) => m.toLowerCase() === lower);
    const should = target.has(g.id);
    if (has === should) continue;
    const next = should
      ? [...models, modelName]
      : models.filter((m) => m.toLowerCase() !== lower);
    await updateModelGroup(g.id, { models: next });
  }
}

/** 上架/管理模型的入参（统一编排：建/改 public_model + 同步分组归属）。 */
export interface ShelveModelInput {
  /** 已上架时的 public_model id（管理场景传，上架场景空） */
  publicModelId?: number;
  publicName: string;
  displayName?: string;
  basePriceRatio: number;
  description?: string;
  enabled: boolean;
  /** 最终应归属的价格分组 id 集合 */
  groupIds: number[];
}

/**
 * 上架/管理模型 mutation：
 * 1) 无 id → createPublicModel；有 id → updatePublicModel；
 * 2) syncModelGroups 覆盖式同步该模型在各组的归属。
 * 失败逐步抛出（公开模型已建但分组未全同步时，可重新「管理」补）。
 */
export function useShelveModel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: ShelveModelInput) => {
      if (input.publicModelId == null) {
        await createPublicModel({
          public_name: input.publicName,
          display_name: input.displayName,
          base_price_ratio: input.basePriceRatio,
          description: input.description,
          enabled: input.enabled,
        });
      } else {
        await updatePublicModel({
          id: input.publicModelId,
          base_price_ratio: input.basePriceRatio,
          display_name: input.displayName,
          description: input.description,
          enabled: input.enabled,
        });
      }
      await syncModelGroups(input.publicName, input.groupIds);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['model-admin'] }),
  });
}

/** 下架模型 mutation（保留分组关联，仅 enabled=false，便于重新上架）。 */
export function useUnshelveModel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (publicModelId: number) =>
      updatePublicModel({ id: publicModelId, enabled: false }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['model-admin'] }),
  });
}
