'use client';

/**
 * features/model/model — 模型广场域：服务端状态 hooks + DTO→视图模型转换。
 *
 * 视图模型层兜底「客户端零泄露」（产品铁律）：
 * /api/pricing 是 PublicView，契约本就不含 cost/profit/上游模型 B/供应商；
 * 这里再用「显式白名单」从 DTO 挑字段构造 VM——即便后端误返敏感字段，也绝不进 VM、不渲染。
 *
 * 价格故事（与 06_prototype 模型广场一致，但只用公开字段）：
 * base_price_ratio = 基准价（USD/1M，折扣=1 口径）。按价格档位推导"省 X%"营销标，
 * 此推导只依赖公开的 base_price_ratio，绝不引用成本 B（成本对客户不可见）。
 */
import { useQuery } from '@tanstack/react-query';
import type { PricingModelEntry, PricingPublicView } from '@/shared/api';
import { ApiError } from '@/shared/api';
import { getPricing } from '../api/model.api';
import {
  CAPABILITY_LABEL,
  FALLBACK_META,
  MODEL_CATALOG,
  type ModelCapability,
} from './catalog';

/** 品质档位（对齐 openapi quality_tier enum: full/max/air）。 */
export type QualityTier = 'full' | 'max' | 'air';

/** 品质档位展示配置（中文名 + 视觉 class key）。 */
export const TIER_DISPLAY: Record<QualityTier, { label: string; cls: 'flagship' | 'enhanced' | 'economy'; note: string }> = {
  full: { label: '旗舰', cls: 'flagship', note: '官方满血，最高质量输出，适合对效果最敏感的核心任务。' },
  max: { label: '增强', cls: 'enhanced', note: '高性价比均衡，质量与成本兼顾，适合大多数生产场景。' },
  air: { label: '经济', cls: 'economy', note: '入门够用，低价档位，适合高并发、低敏感度或预算优先的场景。' },
};

/**
 * 模型广场卡片视图模型（零泄露白名单）。
 * 只含对外可见字段——无 quota_cost / quota_profit / upstream_name / 供应商。
 */
export interface ModelCardVM {
  /** 对外模型名 A */
  modelName: string;
  /** 展示名（display_name 优先，回退 modelName） */
  displayName: string;
  /** 厂商对外品牌名（展示元数据） */
  vendor: string;
  /** 上下文长度展示串 */
  ctx: string;
  /** 能力分类 */
  cats: ModelCapability[];
  /** 能力中文串（如 '对话 · 多模态'） */
  capabilityText: string;
  /** 展示标签 */
  tags: string[];
  /** 品质档位（可能为空，非三档枚举时） */
  tier?: QualityTier;
  /** 同族归组键 */
  family?: string;
  /** 同族展示名 */
  familyLabel?: string;
  /** 基准价（USD/1M，base_price_ratio）；0 或缺失表示"以控制台为准" */
  basePrice: number | null;
  /** 缓存命中价倍率（cache_ratio），可选展示 */
  cacheRatio: number | null;
  /** 营销"省 X%"（基于公开价格档位推导，不涉及成本 B）；无价时 null */
  savePercent: number | null;
}

/** 把契约 quality_tier 字符串收敛到枚举（非法值置 undefined）。 */
function toTier(t: string | undefined): QualityTier | undefined {
  return t === 'full' || t === 'max' || t === 'air' ? t : undefined;
}

/**
 * 价格档位 → "省 X%" 营销标。
 * 仅依赖公开基准价（base_price_ratio）划档，越贵的旗舰模型平台议价空间越大：
 *   基准价 ≥ 10 → 省 85% / 4~10 → 省 80% / >0~4 → 省 70%。
 * 注意：这是基于"公开价格带"的展示性推导，不读取也不暴露任何成本/上游字段。
 */
function deriveSavePercent(basePrice: number | null): number | null {
  if (basePrice == null || basePrice <= 0) return null;
  if (basePrice >= 10) return 85;
  if (basePrice >= 4) return 80;
  return 70;
}

/**
 * DTO（PricingModelEntry）+ 静态展示目录 → 卡片视图模型。
 * 显式白名单：只挑允许字段，杜绝敏感字段透传到 UI。
 */
export function toModelCardVM(entry: PricingModelEntry): ModelCardVM {
  const modelName = entry.model_name ?? '';
  const meta = MODEL_CATALOG[modelName] ?? FALLBACK_META;
  const basePrice =
    typeof entry.base_price_ratio === 'number' ? entry.base_price_ratio : null;
  const cats = meta.cats;
  return {
    modelName,
    displayName: entry.display_name || modelName,
    vendor: meta.vendor,
    ctx: meta.ctx,
    cats,
    capabilityText: cats.map((c) => CAPABILITY_LABEL[c] ?? c).join(' · '),
    tags: meta.tags,
    tier: toTier(entry.quality_tier),
    family: meta.family,
    familyLabel: meta.familyLabel,
    basePrice: basePrice && basePrice > 0 ? basePrice : null,
    cacheRatio: typeof entry.cache_ratio === 'number' ? entry.cache_ratio : null,
    savePercent: deriveSavePercent(basePrice && basePrice > 0 ? basePrice : null),
  };
}

/** 把 PricingPublicView 整体映射成卡片 VM 列表。 */
export function toModelCardVMs(view: PricingPublicView): ModelCardVM[] {
  return (view.models ?? []).map(toModelCardVM);
}

/**
 * 模型广场数据 hook：拉 /api/pricing 并映射成零泄露的卡片 VM 列表。
 * 公开接口，无需鉴权；React Query 管缓存/loading/error。
 */
export function useModelCatalog() {
  return useQuery<ModelCardVM[], ApiError>({
    queryKey: ['pricing', 'models'],
    queryFn: async () => toModelCardVMs(await getPricing()),
    staleTime: 5 * 60 * 1000,
  });
}
