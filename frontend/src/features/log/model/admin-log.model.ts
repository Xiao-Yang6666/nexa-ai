'use client';

/**
 * features/log/model — 管理端全量日志视图模型 + React Query hook（GET /api/log/）。
 *
 * DTO（AdminLogView）→ 表格视图模型。管理后台视图：含全字段（成本/利润/上游 B/供应商），
 * 客户端零泄露铁律只约束 self-scope 客户视图，本 hook 仅在管理后台（adminAuth）使用。
 */
import { useQuery } from '@tanstack/react-query';
import type { AdminLogView } from '@/shared/api';
import { getAdminLogs, type AdminLogQuery } from '../api/log.api';
import { QUOTA_PER_USD } from './log.model';

/** quota（积分）→ USD 数值。 */
function quotaToUsdNum(quota: number | undefined): number {
  return (quota ?? 0) / QUOTA_PER_USD;
}

/** 日志类型枚举 → 展示语义。0=Unknown 1=Topup 2=Consume 3=Manage 4=System 5=Error 6=Refund 7=Login */
export type AdminLogTone = 'consume' | 'manage' | 'login' | 'error' | 'topup' | 'system' | 'refund' | 'unknown';
const TYPE_TO_TONE: Record<number, AdminLogTone> = {
  0: 'unknown',
  1: 'topup',
  2: 'consume',
  3: 'manage',
  4: 'system',
  5: 'error',
  6: 'refund',
  7: 'login',
};

/** 单条管理端日志行视图模型（管理全字段口径）。 */
export interface AdminLogRowVM {
  id: number;
  /** 本地时间文案（MM-DD HH:mm:ss） */
  time: string;
  tone: AdminLogTone;
  /** 用户名 */
  user: string;
  /** 客户请求模型名（C） */
  requested: string;
  /** 实际落到的对外公开模型名（A） */
  resolved: string;
  /** 真实上游模型（B，仅管理可见） */
  upstreamModel: string;
  /** 供应商渠道名（仅管理可见） */
  channelName: string;
  group: string;
  promptTokens: number;
  completionTokens: number;
  /** 客户实付（USD 数值），取 quota_sell（无则回退 quota） */
  sellUsd: number;
  /** 平台成本（USD 数值，仅管理） */
  costUsd: number;
  /** 利润（USD 数值，仅管理） */
  profitUsd: number;
  /** 是否产生费用 */
  hasFee: boolean;
  /** 耗时（ms） */
  useTime: number;
  ip: string;
  userAgent: string;
  requestId: string;
  upstreamRequestId: string;
  /** 是否流式 */
  isStream: boolean;
  /** 成功（非 error 类型）/失败 */
  ok: boolean;
}

function fmtTime(ts: number | undefined): string {
  if (!ts) return '—';
  const d = new Date(ts * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

/** AdminLogView → 行视图模型（管理全字段）。 */
export function toAdminLogRowVM(log: AdminLogView): AdminLogRowVM {
  const type = log.type ?? 0;
  const tone = TYPE_TO_TONE[type] ?? 'unknown';
  const sellQuota = log.quota_sell ?? log.quota ?? 0;
  return {
    id: log.id ?? 0,
    time: fmtTime(log.created_at),
    tone,
    user: log.username || '—',
    requested: log.requested_model || log.model_name || '—',
    resolved: log.resolved_public_model || log.model_name || '—',
    upstreamModel: log.actual_upstream_model || '—',
    channelName: log.channel_name || '—',
    group: log.group || '—',
    promptTokens: log.prompt_tokens ?? 0,
    completionTokens: log.completion_tokens ?? 0,
    sellUsd: quotaToUsdNum(sellQuota),
    costUsd: quotaToUsdNum(log.quota_cost),
    profitUsd: quotaToUsdNum(log.quota_profit),
    hasFee: sellQuota > 0,
    useTime: log.use_time ?? 0,
    ip: log.ip || '—',
    userAgent: log.user_agent || '—',
    requestId: log.request_id || '—',
    upstreamRequestId: log.upstream_request_id || '—',
    isStream: log.is_stream ?? false,
    ok: tone !== 'error',
  };
}

/** 管理端全量日志查询 hook（GET /api/log/）。 */
export function useAdminLogs(query: AdminLogQuery) {
  return useQuery({
    queryKey: ['log', 'admin', query],
    queryFn: () => getAdminLogs(query),
    select: (page) => ({
      rows: page.items.map(toAdminLogRowVM),
      total: page.total ?? 0,
    }),
  });
}
