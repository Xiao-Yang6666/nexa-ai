/**
 * features/provider-account/model/channel-model-cost — 成本倍率域 React Query hooks。
 *
 * 维度：channel_id = account.id（见 api 层注释）。查询 key 用 ['channel-model-cost', ...]，
 * 与账号列表缓存 ['provider-account'] 隔离；写后失效本组 + 账号列表（成本概览列依赖）。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getChannelModelCosts,
  upsertChannelModelCost,
  batchUpsertChannelModelCosts,
  deleteChannelModelCost,
  type ChannelModelCostView,
  type ChannelModelCostWriteRequest,
  type ChannelModelCostListResponse,
} from '../api/channel-model-cost.api';

/** 一次拉全某账号成本行的页大小（账号下模型数有限，足够覆盖）。 */
const COST_PAGE_SIZE = 200;

/**
 * 某账号的成本倍率列表查询 hook（channelId=account.id）。
 * enabled 仅在传入有效 accountId 时触发（编辑抽屉打开后才查）。
 * 返回 { rows, total }，rows 为 ChannelModelCostView[]。
 */
export function useChannelModelCosts(accountId: number | null) {
  return useQuery({
    queryKey: ['channel-model-cost', 'list', accountId],
    queryFn: () => getChannelModelCosts({ channelId: accountId!, pageSize: COST_PAGE_SIZE }),
    enabled: accountId != null && accountId > 0,
    select: (data: ChannelModelCostListResponse) => ({
      rows: data.items ?? [],
      total: data.total ?? 0,
    }),
  });
}

function invalidateCost(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: ['channel-model-cost'] });
  // 成本概览列挂在账号列表上，一并失效。
  qc.invalidateQueries({ queryKey: ['provider-account'] });
}

/** 单条成本 upsert mutation。 */
export function useUpsertChannelModelCost() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ChannelModelCostWriteRequest) => upsertChannelModelCost(req),
    onSuccess: () => invalidateCost(qc),
  });
}

/** 批量成本 upsert mutation（整批一事务）。 */
export function useBatchUpsertChannelModelCosts() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (reqs: ChannelModelCostWriteRequest[]) => batchUpsertChannelModelCosts(reqs),
    onSuccess: () => invalidateCost(qc),
  });
}

/** 删除成本 mutation。 */
export function useDeleteChannelModelCost() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteChannelModelCost(id),
    onSuccess: () => invalidateCost(qc),
  });
}

export type { ChannelModelCostView, ChannelModelCostWriteRequest };
