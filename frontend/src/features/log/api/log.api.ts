/**
 * features/log/api — 日志域接口调用（用户自助调用明细 + 统计）。
 * 路径/方法/出参逐字对齐 openapi.yaml，不臆造字段。
 * 客户端零泄露：UserLogView 已在契约里结构级剔除 quota_cost/quota_profit/上游B/供应商。
 */
import { http } from '@/shared/api';
import type { UserLogView, LogStat, AdminLogView } from '@/shared/api';

/** /api/log/ 管理端全量日志查询参数（对齐 openapi F-4001，分页）。 */
export interface AdminLogQuery {
  /** 日志类型：0=全部 1=Topup 2=Consume 3=Manage 4=System 5=Error 6=Refund 7=Login */
  type?: number;
  start_timestamp?: number;
  end_timestamp?: number;
  username?: string;
  token_name?: string;
  model_name?: string;
  channel?: string;
  group?: string;
  request_id?: string;
  page?: number;
  page_size?: number;
}

/** /api/log/ 返回的分页结构（内联 data：items + total）。 */
export interface AdminLogPage {
  items: AdminLogView[];
  total: number;
}

/**
 * 管理端全量调用日志（全局，分页）。
 * openapi: GET /api/log/ (F-4001, adminAuth) → ApiResponse{ data: { items: AdminLogView[], total } }
 * 管理视图含 B/channel/quota_sell/quota_cost/quota_profit（仅管理后台，非客户端）。
 */
export function getAdminLogs(query: AdminLogQuery = {}): Promise<AdminLogPage> {
  return http.get<AdminLogPage>('/api/log/', {
    query: {
      type: query.type,
      start_timestamp: query.start_timestamp,
      end_timestamp: query.end_timestamp,
      username: query.username,
      token_name: query.token_name,
      model_name: query.model_name,
      channel: query.channel,
      group: query.group,
      request_id: query.request_id,
      page: query.page,
      page_size: query.page_size,
    },
  });
}

/** /api/log/self 查询参数（对齐 openapi F-4002）。 */
export interface LogSelfQuery {
  /** 日志类型：0=Unknown 1=Topup 2=Consume 3=Manage 4=System 5=Error 6=Refund 7=Login */
  type?: number;
  /** 起始时间戳（秒） */
  start_timestamp?: number;
  /** 结束时间戳（秒） */
  end_timestamp?: number;
  token_name?: string;
  model_name?: string;
  group?: string;
  page?: number;
  page_size?: number;
}

/** /api/log/self 返回的分页结构（openapi 内联 data：items + total）。 */
export interface LogSelfPage {
  items: UserLogView[];
  total: number;
}

/**
 * 查询本人调用明细（仅本人，分页）。
 * openapi: GET /api/log/self (F-4002) → ApiResponse{ data: { items: UserLogView[], total } }
 */
export function getSelfLogs(query: LogSelfQuery = {}): Promise<LogSelfPage> {
  return http.get<LogSelfPage>('/api/log/self', {
    query: {
      type: query.type,
      start_timestamp: query.start_timestamp,
      end_timestamp: query.end_timestamp,
      token_name: query.token_name,
      model_name: query.model_name,
      group: query.group,
      page: query.page,
      page_size: query.page_size,
    },
  });
}

/**
 * 查询本人消费日志聚合统计。
 * openapi: GET /api/log/self/stat (F-4005) → ApiResponse{ data: LogStat{ quota, rpm, tpm } }
 */
export function getSelfLogStat(start?: number, end?: number): Promise<LogStat> {
  return http.get<LogStat>('/api/log/self/stat', {
    query: { start_timestamp: start, end_timestamp: end },
  });
}
