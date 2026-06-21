/**
 * features/log/model — 日志域视图模型：调用明细 + 统计。
 *
 * - DTO（UserLogView / LogStat）→ 表格视图模型 + USD 换算。
 * - React Query hooks 管服务端状态。
 * 客户端零泄露：本域仅触达 self-scope 客户视图（契约已剔除 cost/profit/上游B/供应商），
 *   视图模型层不读取、不渲染任何此类字段（即使后端误返也不取）。
 */
import { useQuery } from '@tanstack/react-query';
import type { UserLogView, LogStat } from '@/shared/api';
import {
  getSelfLogs,
  getSelfLogStat,
  type LogSelfQuery,
} from '../api/log.api';

/** quota（积分）→ USD 数值。new-api 惯例 $1 = 500000 quota。 */
export const QUOTA_PER_USD = 500_000;

/** quota → "$x.xxxx" 文案。 */
export function quotaToUsd(quota: number | undefined, dec = 4): string {
  const v = (quota ?? 0) / QUOTA_PER_USD;
  return `$${v.toFixed(dec)}`;
}

/** 日志类型枚举 → 展示徽章（仅客户可见类型）。 */
export type LogTone = 'consume' | 'topup' | 'system' | 'error' | 'refund' | 'login' | 'manage' | 'unknown';

const TYPE_TO_TONE: Record<number, LogTone> = {
  0: 'unknown',
  1: 'topup',
  2: 'consume',
  3: 'manage',
  4: 'system',
  5: 'error',
  6: 'refund',
  7: 'login',
};

/** 单条调用明细视图模型（仅本人、已裁剪口径）。 */
export interface LogRowVM {
  id: number;
  /** 本地时间文案（MM-DD HH:mm:ss） */
  time: string;
  /** 类型语义色 */
  tone: LogTone;
  typeLabel: string;
  /** 客户请求的模型名（C 视角） */
  requested: string;
  /** 实际调用的对外公开模型名（A）；与 requested 同名则视图层不重复渲染 */
  resolved: string;
  /** 分组（折扣等级） */
  group: string;
  promptTokens: number;
  completionTokens: number;
  /** 本笔实付（USD 文案），quota=quota_sell（契约语义） */
  feeUsd: string;
  /** 是否产生费用（0 不渲染金额） */
  hasFee: boolean;
  /** 耗时（ms） */
  useTime: number;
  ip: string;
  userAgent: string;
  requestId: string;
  /** 成功（非 error 类型）/失败 */
  ok: boolean;
}

const TYPE_LABEL: Record<LogTone, string> = {
  consume: '消费',
  topup: '充值',
  system: '系统',
  error: '错误',
  refund: '退款',
  login: '登录',
  manage: '管理',
  unknown: '其他',
};

function fmtTime(ts: number | undefined): string {
  if (!ts) return '—';
  const d = new Date(ts * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

/** UserLogView → 行视图模型（不读取任何成本/利润/上游 B 字段）。 */
export function toLogRowVM(log: UserLogView): LogRowVM {
  const type = log.type ?? 0;
  const tone = TYPE_TO_TONE[type] ?? 'unknown';
  const quota = log.quota ?? 0;
  return {
    id: log.id ?? 0,
    time: fmtTime(log.created_at),
    tone,
    typeLabel: TYPE_LABEL[tone],
    requested: log.requested_model || log.model_name || '—',
    resolved: log.resolved_public_model || log.model_name || '—',
    group: log.group || '—',
    promptTokens: log.prompt_tokens ?? 0,
    completionTokens: log.completion_tokens ?? 0,
    feeUsd: quotaToUsd(quota),
    hasFee: quota > 0,
    useTime: log.use_time ?? 0,
    ip: log.ip || '—',
    userAgent: log.user_agent || '—',
    requestId: log.request_id || '—',
    ok: tone !== 'error',
  };
}

/** 统计视图模型。 */
export interface LogStatVM {
  /** 累计消费 USD 文案 */
  quotaUsd: string;
  /** 每分钟请求数 */
  rpm: number;
  /** 每分钟 token 数 */
  tpm: number;
}

/** LogStat → 统计视图模型。 */
export function toLogStatVM(stat: LogStat | undefined): LogStatVM {
  return {
    quotaUsd: quotaToUsd(stat?.quota, 4),
    rpm: Math.round((stat?.rpm ?? 0) * 100) / 100,
    tpm: Math.round((stat?.tpm ?? 0) * 100) / 100,
  };
}

/** 本人调用明细查询 hook。 */
export function useSelfLogs(query: LogSelfQuery) {
  return useQuery({
    queryKey: ['log', 'self', query],
    queryFn: () => getSelfLogs(query),
    select: (page) => ({
      rows: page.items.map(toLogRowVM),
      total: page.total,
    }),
  });
}

/** 本人统计查询 hook。 */
export function useSelfLogStat(start?: number, end?: number) {
  return useQuery({
    queryKey: ['log', 'self', 'stat', start, end],
    queryFn: () => getSelfLogStat(start, end),
    select: toLogStatVM,
  });
}
