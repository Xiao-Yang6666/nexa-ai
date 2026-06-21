/**
 * features/billing/api — 计费域接口调用（余额 / 消费统计 / 充值下单 / 充值记录）。
 * 路径/方法/出参逐字对齐 openapi.yaml，不臆造字段。
 * 客户端零泄露：所有出参均为 self-scope 客户视图，无成本/利润/供应商。
 */
import { http } from '@/shared/api';
import type { UserView, LogStat, UserLogView, TopUpRequest } from '@/shared/api';

/**
 * 获取本人账户信息（余额 quota / 已用 used_quota / 累计邀请额度）。
 * openapi: GET /api/user/self (F-1045) → ApiResponse{ data: UserView }
 */
export function getSelfAccount(): Promise<UserView> {
  return http.get<UserView>('/api/user/self');
}

/**
 * 本人消费聚合统计（用于本月消费等）。
 * openapi: GET /api/log/self/stat (F-4005) → ApiResponse{ data: LogStat }
 */
export function getSpendStat(start?: number, end?: number): Promise<LogStat> {
  return http.get<LogStat>('/api/log/self/stat', {
    query: { start_timestamp: start, end_timestamp: end },
  });
}

/** /api/log/self 充值记录（type=1 Topup）分页结构。 */
export interface TopupLogPage {
  items: UserLogView[];
  total: number;
}

/**
 * 充值记录（复用本人日志，type=1 Topup）。
 * openapi: GET /api/log/self (F-4002) → ApiResponse{ data: { items, total } }
 */
export function getRechargeLogs(page = 1, pageSize = 20): Promise<TopupLogPage> {
  return http.get<TopupLogPage>('/api/log/self', {
    query: { type: 1, page, page_size: pageSize },
  });
}

/** 充值下单结果（openapi data: TopUpUserView，字段以后端为准，宽松接收）。 */
export interface TopUpResult {
  /** 支付跳转链接 / 二维码（pending） */
  pay_url?: string;
  trade_no?: string;
  [k: string]: unknown;
}

/**
 * 发起充值下单。
 * openapi: POST /api/topup (F-2044) → ApiResponse{ data: TopUpUserView(pending) }
 */
export function createTopUp(req: TopUpRequest): Promise<TopUpResult> {
  return http.post<TopUpResult>('/api/topup', { json: req });
}
