/**
 * features/model/model/model-admin.model — 模型/供应商管理端视图模型 + React Query hooks。
 *
 * 服务于 ModelsAdminPage 四 Tab：对外模型 / 供应商成本 / 模型元数据 / 供应商元数据。
 * DTO→VM 在此收敛；组件只消费 VM，不直接碰 snake_case 契约字段。
 *
 * 客户视图铁律：本域为管理端能力，A→B 映射/上游 B/成本仅 admin 可见——这些 VM 仅在
 * 管理后台（AdminShell）使用，不流向任何客户 self-scope 视图。
 */
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type {
  PublicModelAdminView,
  PublicModelCreateRequest,
  PublicModelUpdateRequest,
  ModelMetaAdminView,
  VendorAdminView,
  ChannelModelCostAdminView,
  PlatformModelMappingAdminView,
  ChannelPoolMember,
} from '@/shared/api';
import {
  getPublicModels,
  createPublicModel,
  updatePublicModel,
  deletePublicModel,
  getModelMappings,
  getChannelPool,
  getChannelModelCosts,
  getModelMetas,
  getMissingModels,
  previewModelSync,
  executeModelSync,
  getVendors,
} from '../api/model-admin.api';

function fmtTime(ts: number | undefined): string {
  if (!ts) return '—';
  const d = new Date(ts * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/* ── 对外模型 Tab ─────────────────────────────────────────────────────── */

/** 品质档（后端枚举 full|max|air）。 */
export type Tier = 'full' | 'max' | 'air';
export const TIER_LABEL: Record<string, string> = {
  full: '旗舰',
  max: '增强',
  air: '经济',
};

/** 对外模型行视图模型（合并 A→B 映射 + 渠道池摘要）。 */
export interface PublicModelVM {
  id: number;
  /** 对外名 A */
  a: string;
  /** 展示名 */
  disp: string;
  tier: Tier | string;
  tierLabel: string;
  /** 基准价倍率文案 */
  priceRatio: string;
  /** 基准价倍率原值（编辑用） */
  priceRatioNum: number;
  /** 排序（编辑用） */
  sortOrder: number;
  /** 描述（编辑用） */
  description: string;
  /** 映射到的真实上游 B（来自 platform_model_mappings，可能多个取首个 + 计数） */
  b: string;
  bCount: number;
  /** 供应渠道池：渠道数 + 主通道名 */
  poolCount: number;
  poolMain: string;
  on: boolean;
}

/** PublicModelAdminView + 映射表 + 渠道池 → 对外模型 VM。 */
export function toPublicModelVM(
  view: PublicModelAdminView,
  mappings: PlatformModelMappingAdminView[],
  pool: ChannelPoolMember[],
): PublicModelVM {
  const a = view.public_name ?? '';
  const myMappings = mappings.filter((m) => m.public_name === a);
  const bList = myMappings.map((m) => m.upstream_name ?? '').filter(Boolean);
  // 渠道池：匹配该 A 映射到的任一 B
  const bSet = new Set(bList);
  const myPool = pool.filter((p) => p.upstream_model && bSet.has(p.upstream_model));
  const sortedPool = myPool.slice().sort((x, y) => (y.priority ?? 0) - (x.priority ?? 0));
  const tier = view.quality_tier ?? 'air';
  return {
    id: view.id ?? 0,
    a,
    disp: view.display_name || a,
    tier,
    tierLabel: TIER_LABEL[tier] ?? tier,
    priceRatio: view.base_price_ratio != null ? `×${Number(view.base_price_ratio).toFixed(2)}` : '—',
    priceRatioNum: view.base_price_ratio != null ? Number(view.base_price_ratio) : 1,
    sortOrder: view.sort_order ?? 0,
    description: view.description || '',
    b: bList[0] ?? '',
    bCount: bList.length,
    poolCount: myPool.length,
    poolMain: sortedPool[0]?.channel_name ?? '',
    on: view.enabled ?? false,
  };
}

/** 对外模型 Tab 聚合查询（public_models + mappings + pool 三源合并）。 */
export function usePublicModels() {
  return useQuery({
    queryKey: ['model-admin', 'public-models'],
    queryFn: async () => {
      const [pm, mp, pool] = await Promise.all([
        getPublicModels(1, 200),
        getModelMappings(1, 500),
        getChannelPool(),
      ]);
      const mappings = mp.items ?? [];
      const poolItems = pool.items ?? [];
      return (pm.items ?? []).map((v) => toPublicModelVM(v, mappings, poolItems));
    },
  });
}

/* ── 供应商成本 Tab ───────────────────────────────────────────────────── */

/** 成本行视图模型（按真实模型 B 分组用）。 */
export interface CostRowVM {
  id: number;
  channelId: number;
  /** 真实模型 B */
  b: string;
  /** 成本倍率文案 */
  cost: string;
  costNum: number | null;
  /** 是否未配（cost 缺失） */
  unset: boolean;
  on: boolean;
  upd: string;
  remark: string;
}

/** 成本按 B 分组后的视图模型。 */
export interface CostGroupVM {
  b: string;
  rows: CostRowVM[];
}

export function toCostRowVM(view: ChannelModelCostAdminView): CostRowVM {
  const c = view.cost_ratio;
  return {
    id: view.id ?? 0,
    channelId: view.channel_id ?? 0,
    b: view.upstream_model ?? '',
    cost: c != null ? Number(c).toFixed(2) : '',
    costNum: c != null ? Number(c) : null,
    unset: c == null,
    on: view.enabled ?? false,
    upd: fmtTime(view.updated_time),
    remark: view.remark || '',
  };
}

/** 供应商成本 Tab 查询（按 B 分组）。 */
export function useChannelCosts() {
  return useQuery({
    queryKey: ['model-admin', 'costs'],
    queryFn: async () => {
      const res = await getChannelModelCosts({ page: 1, pageSize: 500 });
      const rows = (res.items ?? []).map(toCostRowVM);
      const groups = new Map<string, CostRowVM[]>();
      rows.forEach((r) => {
        const arr = groups.get(r.b) ?? [];
        arr.push(r);
        groups.set(r.b, arr);
      });
      return Array.from(groups.entries()).map(([b, gr]) => ({ b, rows: gr }) as CostGroupVM);
    },
  });
}

/* ── 模型元数据 Tab ───────────────────────────────────────────────────── */

export type ModelState = 'on' | 'off' | 'pre';
export const MODEL_STATE_MAP: Record<ModelState, { cls: string; tone: string; lab: string }> = {
  on: { cls: 'b-suc', tone: '--color-success', lab: '上架' },
  off: { cls: 'b-dan', tone: '--color-danger', lab: '下架' },
  pre: { cls: 'b-warn', tone: '--color-warning', lab: '预发布' },
};

/** 由后端 status 码派生展示状态。1=上架 / 0或2=下架 / 3=预发布（按 new-api 习惯，未知回落下架）。 */
function deriveModelState(status: number | undefined): ModelState {
  if (status === 1) return 'on';
  if (status === 3) return 'pre';
  return 'off';
}

export interface ModelMetaVM {
  id: number;
  nm: string;
  vendorId: number;
  /** 供应商名（由 vendor 列表 join） */
  ven: string;
  st: ModelState;
  tags: string[];
  endpoints: string;
}

export function toModelMetaVM(view: ModelMetaAdminView, vendorName: Map<number, string>): ModelMetaVM {
  return {
    id: view.id ?? 0,
    nm: view.model_name ?? '',
    vendorId: view.vendor_id ?? 0,
    ven: view.vendor_id ? (vendorName.get(view.vendor_id) ?? `#${view.vendor_id}`) : '—',
    st: deriveModelState(view.status),
    tags: (view.tags || '').split(',').map((t) => t.trim()).filter(Boolean),
    endpoints: view.endpoints || '',
  };
}

/** 模型元数据 Tab 查询（models join vendors 取供应商名）。 */
export function useModelMetas() {
  return useQuery({
    queryKey: ['model-admin', 'metas'],
    queryFn: async () => {
      const [models, vendors] = await Promise.all([getModelMetas(1, 200), getVendors(1, 200)]);
      const vendorName = new Map<number, string>();
      (vendors.items ?? []).forEach((v) => {
        if (v.id != null) vendorName.set(v.id, v.name ?? `#${v.id}`);
      });
      return (models.items ?? []).map((m) => toModelMetaVM(m, vendorName));
    },
  });
}

/* ── 供应商元数据 Tab ─────────────────────────────────────────────────── */

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

/* ── 缺失检测 / 同步预览 / 同步执行 ──────────────────────────────────── */

/** 缺失模型检测 mutation（按需触发）。 */
export function useMissingModels() {
  return useMutation({ mutationFn: () => getMissingModels() });
}

/** 上游同步预览 mutation。 */
export function useModelSyncPreview() {
  return useMutation({ mutationFn: (locale?: string) => previewModelSync(locale) });
}

/** 上游同步执行 mutation。成功后刷新模型管理域。 */
export function useModelSyncExecute() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: { overwrite?: boolean; models?: string[] } = {}) => executeModelSync(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['model-admin'] }),
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
