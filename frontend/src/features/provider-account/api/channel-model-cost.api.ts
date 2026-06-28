/**
 * features/provider-account/api/channel-model-cost — 供应商成本倍率接口调用（管理端，AdminAuth）。
 * 路径/方法/字段对齐后端 ChannelModelCostController（/api/channel_model_costs*）。
 *
 * ⚠️ 维度说明：成本表名沿用历史 `channel_model_costs`，但 round7「渠道整合进供应商账号」后，
 * 其 `channel_id` 字段实际存的就是 `account.id`（转发链路 RelayForwardUseCase 直接以
 * account.id 当 channelId 查成本）。本前端按 account.id 接入，channel_id 即账号 id。
 *
 * B 不可见三道闸：本组端点含 upstream_model(B) + cost_ratio，仅 admin/root 可达，客户无读路径。
 * 字段经全局 Jackson SNAKE_CASE 序列化，前端统一 snake_case。
 */
import { http } from '@/shared/api';

/** 成本倍率管理视图（AdminView，含 cost_ratio + upstream_model=B，绝不下发客户）。 */
export interface ChannelModelCostView {
  id: number;
  /** = account.id（历史字段名 channel_id） */
  channel_id: number;
  /** 上游真实模型名 B */
  upstream_model: string;
  /** 输入成本倍率（相对基准价；缺省视为缺失记 0+告警） */
  cost_ratio?: number | null;
  /** 输出成本倍率（0=回落 cost_ratio×现网 completion_ratio） */
  completion_cost_ratio?: number | null;
  enabled?: boolean | null;
  effective_time?: number | null;
  source_unit_price?: number | null;
  remark?: string | null;
  created_time?: number | null;
  updated_time?: number | null;
}

/** 成本倍率写请求（upsert：同 channel_id+upstream_model 已有则更新）。 */
export interface ChannelModelCostWriteRequest {
  /** = account.id */
  channel_id: number;
  upstream_model: string;
  cost_ratio?: number;
  /** 0=回落 cost_ratio×现网 completion_ratio */
  completion_cost_ratio?: number;
  enabled?: boolean;
  remark?: string;
}

/** 成本列表响应（后端 ChannelModelCostListView：items + total）。 */
export interface ChannelModelCostListResponse {
  items: ChannelModelCostView[];
  total: number;
}

/**
 * 成本倍率列表。
 * GET /api/channel_model_costs?channel_id=&upstream_model=&p=&page_size=
 *   → ApiResponse{ data: { items, total } }
 * 按账号（channel_id=account.id）拉取该账号全部成本行；page_size 给大值一次拉全。
 */
export function getChannelModelCosts(params: {
  channelId?: number;
  upstreamModel?: string;
  page?: number;
  pageSize?: number;
} = {}): Promise<ChannelModelCostListResponse> {
  return http.get<ChannelModelCostListResponse>('/api/channel_model_costs', {
    query: {
      channel_id: params.channelId,
      upstream_model: params.upstreamModel,
      p: params.page,
      page_size: params.pageSize,
    },
  });
}

/**
 * 创建/更新单条成本倍率（upsert）。
 * POST /api/channel_model_costs → ApiResponse{ data: ChannelModelCostView }
 */
export function upsertChannelModelCost(
  req: ChannelModelCostWriteRequest,
): Promise<ChannelModelCostView> {
  return http.post<ChannelModelCostView>('/api/channel_model_costs', { json: req });
}

/**
 * 批量创建/更新成本倍率（整批一事务 upsert）。
 * POST /api/channel_model_costs/batch → ApiResponse{ data: ChannelModelCostView[] }
 * 前端「批量设成本倍率」把选中账号 × 各自支持模型展开为条目数组传入。
 */
export function batchUpsertChannelModelCosts(
  reqs: ChannelModelCostWriteRequest[],
): Promise<ChannelModelCostView[]> {
  return http.post<ChannelModelCostView[]>('/api/channel_model_costs/batch', { json: reqs });
}

/**
 * 删除成本倍率（删后视为缺失记 0+告警）。
 * DELETE /api/channel_model_costs/{id} → ApiResponse
 */
export function deleteChannelModelCost(id: number): Promise<void> {
  return http.delete<void>(`/api/channel_model_costs/${id}`);
}
