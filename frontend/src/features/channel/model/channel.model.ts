/**
 * features/channel/model — 渠道管理域视图模型 + React Query hooks。
 *
 * - ChannelAdminView → 列表行视图模型（状态由 status 码派生）。
 * - 渠道 type 整数码 → 友好类型名（new-api ChannelType 常量子集，其余按"其他"）。
 * - 余额/已用为 quota 单位（new-api 惯例 $1 = 500000 quota）→ USD 展示。
 * - 后端 status 码：1=启用 / 2=手动禁用 / 3=自动禁用。"限流告警"非独立状态码，
 *   本系统暂以 status 三态呈现（无 warn 态时不再臆造）。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type {
  ChannelAdminView,
  ChannelCreateRequest,
  ChannelUpdateRequest,
  ChannelModelCostAdminView,
  ChannelModelCostWriteRequest,
} from '@/shared/api';
import {
  getChannels,
  createChannel,
  updateChannel,
  deleteChannel,
  batchOperateChannels,
  getChannelCosts,
  upsertChannelCost,
  batchUpsertChannelCosts,
  type ChannelListResponse,
} from '../api/channel.api';

/** quota（积分）→ USD。new-api 惯例 $1 = 500000 quota。 */
export const QUOTA_PER_USD = 500_000;

/** 渠道展示状态。 */
export type ChannelStatus = 'on' | 'man' | 'auto';

/** 由后端 status 整数码派生展示状态。1=启用 / 2=手动禁用 / 3=自动禁用。 */
export function deriveChannelStatus(status: number | undefined): ChannelStatus {
  if (status === 2) return 'man';
  if (status === 3) return 'auto';
  return 'on';
}

/**
 * new-api 渠道 type 整数码 → 友好类型名。
 * 仅收敛常见类型码（取自 new-api ChannelType 常量），未知 type 回落为 "type N"。
 */
const TYPE_NAME: Record<number, string> = {
  1: 'OpenAI',
  3: 'Azure OpenAI',
  14: 'Anthropic',
  24: 'Google Gemini',
  25: 'Google Vertex',
  8: 'OpenAI 兼容',
  41: 'Ollama',
  54: 'OpenAI Codex',
};

export function channelTypeName(type: number | undefined): string {
  if (type == null) return '—';
  return TYPE_NAME[type] ?? `type ${type}`;
}

/** 类型下拉选项（用于筛选/新建）。 */
export const TYPE_OPTIONS: { code: number; label: string }[] = [
  { code: 1, label: 'OpenAI' },
  { code: 3, label: 'Azure OpenAI' },
  { code: 14, label: 'Anthropic' },
  { code: 24, label: 'Google Gemini' },
  { code: 25, label: 'Google Vertex' },
  { code: 8, label: 'OpenAI 兼容' },
  { code: 41, label: 'Ollama' },
  { code: 54, label: 'OpenAI Codex' },
];

/** 渠道列表行视图模型。 */
export interface ChannelRowVM {
  id: number;
  name: string;
  /** 友好类型名 */
  type: string;
  /** 原始 type 码（编辑用） */
  typeCode: number;
  st: ChannelStatus;
  /** 优先级 */
  pr: number;
  /** 权重 */
  wt: number;
  /** 已用 USD 文案 */
  used: string;
  /** 余额 USD 文案 */
  bal: string;
  /** 响应延迟 ms（0=无测试记录） */
  lat: number;
  /** 最近测试时间文案 */
  testAt: string;
  /** 支持模型集（逗号分隔原文，编辑用） */
  models: string;
  /** 上游 baseUrl（编辑用） */
  baseUrl: string;
  /** 模型映射 JSON（编辑用） */
  modelMapping: string;
  /** 分组（编辑用） */
  group: string;
}

function quotaToUsd(quota: number | undefined): string {
  return `$${((quota ?? 0) / QUOTA_PER_USD).toFixed(2)}`;
}

function fmtTime(ts: number | undefined): string {
  if (!ts) return '—';
  const d = new Date(ts * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** ChannelAdminView → 列表行视图模型。 */
export function toChannelRowVM(view: ChannelAdminView): ChannelRowVM {
  return {
    id: view.id ?? 0,
    name: view.name || '—',
    type: channelTypeName(view.type),
    typeCode: view.type ?? 0,
    st: deriveChannelStatus(view.status),
    pr: view.priority ?? 0,
    wt: view.weight ?? 0,
    used: quotaToUsd(view.used_quota),
    // balance 后端已是 USD（BigDecimal），非 quota；直接展示
    bal: view.balance != null ? `$${Number(view.balance).toFixed(2)}` : '—',
    lat: view.response_time ?? 0,
    testAt: fmtTime(view.test_time),
    models: view.models || '',
    baseUrl: view.base_url || '',
    modelMapping: view.model_mapping || '',
    group: view.group || 'default',
  };
}

/* ── React Query hooks ─────────────────────────────────────────────────── */

/** 渠道列表查询 hook。返回 { rows, total }。 */
export function useChannels(params: {
  page?: number;
  pageSize?: number;
  group?: string;
  type?: number;
  tag?: string;
  status?: number;
} = {}) {
  return useQuery({
    queryKey: ['channel', 'list', params],
    queryFn: () => getChannels(params),
    select: (data: ChannelListResponse) => ({
      rows: (data.items ?? []).map(toChannelRowVM),
      total: data.total ?? 0,
    }),
  });
}

/** 创建渠道 mutation。 */
export function useCreateChannel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ChannelCreateRequest) => createChannel(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['channel'] }),
  });
}

/** 编辑渠道 mutation。 */
export function useUpdateChannel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ChannelUpdateRequest) => updateChannel(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['channel'] }),
  });
}

/** 删除渠道 mutation。 */
export function useDeleteChannel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteChannel(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['channel'] }),
  });
}

/** 批量操作渠道 mutation（action: enable|disable|delete）。 */
export function useBatchOperateChannels() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ ids, action }: { ids: number[]; action: string }) =>
      batchOperateChannels(ids, action),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['channel'] }),
  });
}

/* ── 渠道成本倍率（channel × B，仅 admin） ───────────────────────────────── */

/** 单渠道成本行视图模型（B + 成本倍率）。 */
export interface ChannelCostVM {
  id: number;
  /** 真实模型 B（渠道 modelMapping 产出的上游名） */
  b: string;
  /** 成本倍率原值（null=未配） */
  costNum: number | null;
  enabled: boolean;
  remark: string;
}

export function toChannelCostVM(view: ChannelModelCostAdminView): ChannelCostVM {
  const c = view.cost_ratio;
  return {
    id: view.id ?? 0,
    b: view.upstream_model ?? '',
    costNum: c != null ? Number(c) : null,
    enabled: view.enabled ?? false,
    remark: view.remark || '',
  };
}

/** 某渠道全部成本行查询（按 channel_id 过滤）。 */
export function useChannelCosts(channelId: number | null) {
  return useQuery({
    queryKey: ['channel', 'costs', channelId],
    enabled: channelId != null,
    queryFn: () => getChannelCosts({ channelId: channelId ?? undefined, pageSize: 200 }),
    select: (data) => (data.items ?? []).map(toChannelCostVM),
  });
}

/** 单条成本 upsert mutation。 */
export function useUpsertChannelCost() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ChannelModelCostWriteRequest) => upsertChannelCost(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['channel', 'costs'] }),
  });
}

/** 批量成本 upsert mutation（按账号/分组批量设倍率）。 */
export function useBatchUpsertChannelCosts() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (items: ChannelModelCostWriteRequest[]) => batchUpsertChannelCosts(items),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['channel', 'costs'] }),
  });
}
