/**
 * features/growth/api — 增长域接口调用（签到 + 邀请）。
 * 路径/方法/出参逐字对齐 openapi.yaml，不臆造字段。
 */
import { http } from '@/shared/api';
import type { CheckinStatusView, CheckinResult } from '@/shared/api';

/**
 * 查询签到状态与本月记录。
 * openapi: GET /api/user/checkin (F-1047) → ApiResponse{ data: CheckinStatusView }
 */
export function getCheckinStatus(month?: string): Promise<CheckinStatusView> {
  return http.get<CheckinStatusView>('/api/user/checkin', {
    query: month ? { month } : undefined,
  });
}

/**
 * 每日签到领取随机额度。
 * openapi: POST /api/user/checkin (F-1046) → ApiResponse{ data: CheckinResult }
 */
export function postCheckin(): Promise<CheckinResult> {
  return http.post<CheckinResult>('/api/user/checkin');
}

/**
 * 获取个人邀请码。
 * openapi: GET /api/user/self/aff (F-1039) → ApiResponse{ data: string }
 */
export function getAffCode(): Promise<string> {
  return http.get<string>('/api/user/self/aff');
}
