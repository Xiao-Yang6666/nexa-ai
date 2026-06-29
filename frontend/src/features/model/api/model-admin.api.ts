/**
 * features/model/api/model-admin.api — 模型/供应商管理端接口调用（AdminAuth）。
 * 路径/方法/出参逐字对齐 openapi.yaml，不臆造字段。分页参数统一 p + page_size。
 *
 * 覆盖 ModelsAdminPage 三 Tab 所需端点：
 *  - 对外模型 public_models（可用分组由模型组 model_group 反查，见 model 层）
 *  - 模型元数据 models（含 sync/preview、missing）
 *  - 模型厂牌 vendors
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
