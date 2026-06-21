/**
 * features/redeem/api — 兑换码管理域接口调用（管理端，AdminAuth）。
 * 路径/方法/出参逐字对齐 openapi.yaml，不臆造字段。
 */
import { http } from '@/shared/api';
import type { RedemptionAdminView, RedemptionCreateRequest } from '@/shared/api';

/** /api/redemption/ 分页结构（对齐后端 PageView：items + total + page + page_size）。 */
export interface RedemptionPage {
  items: RedemptionAdminView[];
  total: number;
  page: number;
  page_size: number;
}

/**
 * 兑换码分页列表（管理端）。
 * openapi: GET /api/redemption/ (F-2045, adminAuth) → ApiResponse{ data: PageView<RedemptionAdminView> }
 * 注意分页参数为 p（页码）+ page_size。
 */
export function getRedemptions(page = 1, pageSize = 20): Promise<RedemptionPage> {
  return http.get<RedemptionPage>('/api/redemption/', {
    query: { p: page, page_size: pageSize },
  });
}

/**
 * 生成兑换码（单个/批量）。
 * openapi: POST /api/redemption/ (F-2045, adminAuth) → ApiResponse{ data: string[] }（明文 key 列表）
 */
export function generateRedemptions(req: RedemptionCreateRequest): Promise<string[]> {
  return http.post<string[]>('/api/redemption/', { json: req });
}
