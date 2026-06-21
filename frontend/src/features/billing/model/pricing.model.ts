'use client';

/**
 * features/billing/model — 价格页域：服务端状态 hook + 价格视图模型。
 *
 * 接 /api/pricing(PublicView)，零泄露：契约本就不含 cost/profit/上游 B/供应商。
 * 价格对比表展示「基准价」与基于公开价格带推导的「省 X%」营销标，不引用成本。
 */
import { useQuery } from '@tanstack/react-query';
import type { PricingModelEntry, PricingPublicView } from '@/shared/api';
import { ApiError } from '@/shared/api';
import { getPricing } from '@/features/model/api/model.api';

/** 价格对比行视图（零泄露白名单）。 */
export interface PriceRowVM {
  modelName: string;
  displayName: string;
  /** 基准价（USD/1M）；null 表示以控制台为准 */
  basePrice: number | null;
  /** 营销"省 X%"（公开价格带推导，不涉成本）；null 无价 */
  savePercent: number | null;
  /** 品质档（full/max/air），用于同族分行标记 */
  tier?: string;
}

/** 价格带 → 省 X%（与 model 域 deriveSavePercent 一致口径）。 */
function deriveSavePercent(basePrice: number | null): number | null {
  if (basePrice == null || basePrice <= 0) return null;
  if (basePrice >= 10) return 85;
  if (basePrice >= 4) return 80;
  return 70;
}

/** DTO → 价格行 VM（白名单挑字段）。 */
export function toPriceRowVM(entry: PricingModelEntry): PriceRowVM {
  const basePrice =
    typeof entry.base_price_ratio === 'number' && entry.base_price_ratio > 0
      ? entry.base_price_ratio
      : null;
  return {
    modelName: entry.model_name ?? '',
    displayName: entry.display_name || entry.model_name || '',
    basePrice,
    savePercent: deriveSavePercent(basePrice),
    tier: entry.quality_tier,
  };
}

export function toPriceRowVMs(view: PricingPublicView): PriceRowVM[] {
  return (view.models ?? []).map(toPriceRowVM);
}

/** 价格页数据 hook：拉 /api/pricing → 价格行 VM 列表（零泄露）。 */
export function usePricing() {
  return useQuery<PriceRowVM[], ApiError>({
    queryKey: ['pricing', 'rows'],
    queryFn: async () => toPriceRowVMs(await getPricing()),
    staleTime: 5 * 60 * 1000,
  });
}
