/**
 * features/channel/api — 渠道管理域接口调用（管理端，AdminAuth）。
 * 路径/方法/出参逐字对齐 openapi.yaml，不臆造字段。
 *
 * 出参 ChannelAdminView 为管理端视图（key 已脱敏；balance 为渠道余额运维数据）。
 */
import { http } from '@/shared/api';
import type {
  ChannelAdminView,
  ChannelCreateRequest,
  ChannelUpdateRequest,
  ChannelModelCostAdminView,
  ChannelModelCostWriteRequest,
} from '@/shared/api';

/** /api/channel/ 列表响应（后端 ChannelListView：items + total）。 */
export interface ChannelListResponse {
  items: ChannelAdminView[];
  total: number;
}

/**
 * 渠道列表分页。
 * openapi: GET /api/channel/ (F-2016, adminAuth) → ApiResponse{ data: { items, total } }
 * 分页参数 p（页码）+ page_size，支持 group/type/tag/status 过滤。
 */
export function getChannels(params: {
  page?: number;
  pageSize?: number;
  group?: string;
  type?: number;
  tag?: string;
  status?: number;
} = {}): Promise<ChannelListResponse> {
  return http.get<ChannelListResponse>('/api/channel/', {
    query: {
      p: params.page,
      page_size: params.pageSize,
      group: params.group,
      type: params.type,
      tag: params.tag,
      status: params.status,
    },
  });
}

/**
 * 创建渠道。
 * openapi: POST /api/channel/ (F-2016) → ApiResponse{ data: ChannelAdminView }
 */
export function createChannel(req: ChannelCreateRequest): Promise<ChannelAdminView> {
  return http.post<ChannelAdminView>('/api/channel/', { json: req });
}

/**
 * 编辑渠道（覆盖式）。
 * openapi: PUT /api/channel/ (F-2016) → ApiResponse{ data: ChannelAdminView }
 */
export function updateChannel(req: ChannelUpdateRequest): Promise<ChannelAdminView> {
  return http.put<ChannelAdminView>('/api/channel/', { json: req });
}

/**
 * 删除渠道。
 * openapi: DELETE /api/channel/{id} (F-2016) → ApiResponse
 */
export function deleteChannel(id: number): Promise<void> {
  return http.delete<void>(`/api/channel/${id}`);
}

/**
 * 批量操作渠道（启用/禁用/删除）。
 * openapi: POST /api/channel/batch (F-2016) → ApiResponse{ data: number }（受影响数量）
 */
export function batchOperateChannels(ids: number[], action: string): Promise<number> {
  return http.post<number>('/api/channel/batch', { json: { ids, action } });
}

/**
 * 单渠道连通性测试。
 * openapi: GET /api/channel/test/{id} (F-2017) → ApiResponse{ data: ChannelTestResultView }
 */
export function testChannel(id: number, model?: string): Promise<unknown> {
  return http.get<unknown>(`/api/channel/test/${id}`, {
    query: model ? { model } : undefined,
  });
}

/**
 * 按参数探测上游模型列表（新建渠道场景，无需先保存）。
 * openapi: POST /api/channel/fetch_models (F-2026) → ApiResponse{ data: string[] }
 */
export function fetchUpstreamModels(req: {
  type: number;
  baseUrl?: string;
  key: string;
}): Promise<string[]> {
  return http.post<string[]>('/api/channel/fetch_models', {
    json: { type: req.type, base_url: req.baseUrl || undefined, key: req.key },
  });
}

/* ── 渠道成本倍率（channel × 真实模型 B，仅 admin，客户不可见） ── */

/** 成本列表响应（后端 ChannelModelCostListView：items + total）。 */
export interface ChannelCostListResponse {
  items: ChannelModelCostAdminView[];
  total: number;
}

/**
 * 查渠道成本倍率（按 channel_id 过滤取某渠道全部 B 成本）。
 * openapi: GET /api/channel_model_costs (F-6006, adminAuth)
 */
export function getChannelCosts(params: {
  channelId?: number;
  upstreamModel?: string;
  page?: number;
  pageSize?: number;
} = {}): Promise<ChannelCostListResponse> {
  return http.get<ChannelCostListResponse>('/api/channel_model_costs', {
    query: {
      channel_id: params.channelId,
      upstream_model: params.upstreamModel,
      p: params.page,
      page_size: params.pageSize,
    },
  });
}

/**
 * 创建/更新单条成本倍率（upsert，同 channel×B 覆盖）。
 * openapi: POST /api/channel_model_costs (F-6006)
 */
export function upsertChannelCost(req: ChannelModelCostWriteRequest): Promise<ChannelModelCostAdminView> {
  return http.post<ChannelModelCostAdminView>('/api/channel_model_costs', { json: req });
}

/**
 * 批量创建/更新成本倍率（按账号/分组批量设，逐条 upsert，整批一事务）。
 * openapi: POST /api/channel_model_costs/batch
 */
export function batchUpsertChannelCosts(
  items: ChannelModelCostWriteRequest[],
): Promise<ChannelModelCostAdminView[]> {
  return http.post<ChannelModelCostAdminView[]>('/api/channel_model_costs/batch', { json: items });
}
