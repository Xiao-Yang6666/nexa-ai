/**
 * features/model/api/model-admin.api — 模型/供应商管理端接口调用（AdminAuth）。
 * 路径/方法/出参逐字对齐 openapi.yaml，不臆造字段。分页参数统一 p + page_size。
 *
 * 覆盖 ModelsAdminPage 四 Tab 所需端点：
 *  - 对外模型 public_models（渠道池 channel/pool 按对外名 A 匹配；A→B 映射已下沉渠道级）
 *  - 供应商成本 channel_model_costs
 *  - 模型元数据 models（含 sync/preview、missing）
 *  - 模型厂牌 vendors
 *
 * A→B 底仓映射已下沉为渠道级（channel.model_mapping），不再有全局 platform_model_mappings 端点。
 */
import { http } from '@/shared/api';
import type {
  PublicModelAdminView,
  PublicModelCreateRequest,
  PublicModelUpdateRequest,
  ModelMetaAdminView,
  ModelMetaCreateRequest,
  ModelMetaUpdateRequest,
  VendorAdminView,
  ChannelModelCostAdminView,
  ChannelPoolMember,
  ModelSyncDiff,
  ModelSyncResult,
} from '@/shared/api';

/* ── 对外模型 ─────────────────────────────────────────────────────────── */
export interface PublicModelListResponse {
  items: PublicModelAdminView[];
  total: number;
}
/** GET /api/public_models (F-6001) */
export function getPublicModels(page = 1, pageSize = 50): Promise<PublicModelListResponse> {
  return http.get<PublicModelListResponse>('/api/public_models', {
    query: { p: page, page_size: pageSize },
  });
}

/** POST /api/public_models (F-6001) 创建对外模型。 */
export function createPublicModel(req: PublicModelCreateRequest): Promise<PublicModelAdminView> {
  return http.post<PublicModelAdminView>('/api/public_models', { json: req });
}

/** PUT /api/public_models (F-6001/F-6004) 更新对外模型（A 不可改）。 */
export function updatePublicModel(req: PublicModelUpdateRequest): Promise<PublicModelAdminView> {
  return http.put<PublicModelAdminView>('/api/public_models', { json: req });
}

/** DELETE /api/public_models/{id} (F-6001) 删除对外模型。 */
export function deletePublicModel(id: number): Promise<void> {
  return http.delete<void>(`/api/public_models/${id}`);
}

/* ── 渠道池 ───────────────────────────────────────────────────────────── */
export interface ChannelPoolListResponse {
  items: ChannelPoolMember[];
}
/** GET /api/channel/pool (F-6005)。无 total，不分页。 */
export function getChannelPool(params: {
  publicName?: string;
  upstreamModel?: string;
  group?: string;
} = {}): Promise<ChannelPoolListResponse> {
  return http.get<ChannelPoolListResponse>('/api/channel/pool', {
    query: {
      public_name: params.publicName,
      upstream_model: params.upstreamModel,
      group: params.group,
    },
  });
}

/* ── 渠道列表（供"供应渠道池"本地匹配用，避免渠道池端点 N+1 + 静默吞错） ── */
export interface ChannelListForPool {
  items: Array<{ id?: number; name?: string; models?: string; group?: string; priority?: number; status?: number }>;
  total: number;
}
/** GET /api/channel/ —— 一次性拉全量渠道（page_size 取大值），前端按 models CSV 本地匹配每个对外模型 A。 */
export function getChannelsForPool(pageSize = 500): Promise<ChannelListForPool> {
  return http.get<ChannelListForPool>('/api/channel/', {
    query: { p: 1, page_size: pageSize },
  });
}

/* ── 供应商成本 ───────────────────────────────────────────────────────── */
export interface CostListResponse {
  items: ChannelModelCostAdminView[];
  total: number;
}
/** GET /api/channel_model_costs (F-6006) */
export function getChannelModelCosts(params: {
  channelId?: number;
  upstreamModel?: string;
  page?: number;
  pageSize?: number;
} = {}): Promise<CostListResponse> {
  return http.get<CostListResponse>('/api/channel_model_costs', {
    query: {
      channel_id: params.channelId,
      upstream_model: params.upstreamModel,
      p: params.page,
      page_size: params.pageSize,
    },
  });
}

/* ── 模型元数据 ───────────────────────────────────────────────────────── */
export interface ModelMetaListResponse {
  items: ModelMetaAdminView[];
  total: number;
  vendor_counts?: Record<string, number>;
}
/** GET /api/models (F-3013)。含 vendor_counts。 */
export function getModelMetas(page = 1, pageSize = 50): Promise<ModelMetaListResponse> {
  return http.get<ModelMetaListResponse>('/api/models', {
    query: { p: page, page_size: pageSize },
  });
}

/** GET /api/models/missing (F-3021)。返回上游存在但本地缺失的模型名数组。 */
export function getMissingModels(): Promise<string[]> {
  return http.get<string[]>('/api/models/missing');
}

/** POST /api/models (F-3015) 创建模型元数据。 */
export function createModelMeta(req: ModelMetaCreateRequest): Promise<ModelMetaAdminView> {
  return http.post<ModelMetaAdminView>('/api/models', { json: req });
}

/** PUT /api/models (F-3016) 更新模型元数据（支持 status_only）。 */
export function updateModelMeta(req: ModelMetaUpdateRequest): Promise<ModelMetaAdminView> {
  return http.put<ModelMetaAdminView>('/api/models', { json: req });
}

/** DELETE /api/models/{id} (F-3017) 删除模型元数据。 */
export function deleteModelMeta(id: number): Promise<void> {
  return http.delete<void>(`/api/models/${id}`);
}

/** POST /api/models/sync/preview (F-3020)。返回同步差异。 */
export function previewModelSync(locale?: string): Promise<ModelSyncDiff> {
  return http.post<ModelSyncDiff>('/api/models/sync/preview', {
    json: locale ? { locale } : {},
  });
}

/** POST /api/models/sync (F-3019) 执行上游同步。models 为空=全量。 */
export function executeModelSync(req: { overwrite?: boolean; models?: string[] } = {}): Promise<ModelSyncResult> {
  return http.post<ModelSyncResult>('/api/models/sync', { json: req });
}

/* ── 供应商元数据 ─────────────────────────────────────────────────────── */
export interface VendorListResponse {
  items: VendorAdminView[];
  total: number;
}
/** GET /api/vendors (F-3018) */
export function getVendors(page = 1, pageSize = 100): Promise<VendorListResponse> {
  return http.get<VendorListResponse>('/api/vendors', {
    query: { p: page, page_size: pageSize },
  });
}
