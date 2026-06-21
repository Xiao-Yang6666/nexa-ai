/**
 * features/model/api — 模型/价格域接口调用（基于 shared/api 的 http）。
 * 路径/方法/出参逐字对齐 openapi.yaml，不臆造字段。
 */
import { http } from '@/shared/api';
import type { PricingPublicView } from '@/shared/api';

/**
 * 公开模型价格页。
 * openapi: GET /api/pricing (F-2048, security:[]) → ApiResponse{ data: PricingPublicView }。
 * PublicView 契约层已裁掉 B/成本/供应商——客户端零泄露。
 */
export function getPricing(locale?: string): Promise<PricingPublicView> {
  return http.get<PricingPublicView>('/api/pricing', {
    query: locale ? { locale } : undefined,
  });
}
