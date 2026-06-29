'use client';

/**
 * features/model/model — 模型广场域：服务端状态 hooks + DTO→视图模型转换。
 *
 * 视图模型层兜底「客户端零泄露」（产品铁律）：
 * /api/pricing 是 PublicView，契约本就不含 cost/profit/上游模型 B/供应商；
 * 这里再用「显式白名单」从 DTO 挑字段构造 VM——即便后端误返敏感字段，也绝不进 VM、不渲染。
 *
 * 定价模型（重设计后）：一个真实模型一条记录（不再按品质档拆条）；差异定价由「价格分组」承载——
 * 同一模型加入多个可见分组、各组设不同倍率。groups[] 即该模型在各可见分组的价格对比，
 * 详情抽屉据此渲染「分组价格对比表」。所有价格只依赖公开的 base_price_ratio × 分组倍率，
 * 绝不引用成本 B（成本对客户不可见）。
 */
import { useQuery } from '@tanstack/react-query';
import type { PricingModelEntry, PricingPublicView } from '@/shared/api';
import { ApiError } from '@/shared/api';
import { getPricing } from '../api/model.api';
import {
  CAPABILITY_LABEL,
  resolveCatalogMeta,
  type ModelCapability,
} from './catalog';

/**
 * 模型在某可用分组里的价格（分组价格对比条目，零泄露白名单）。
 */
export interface ModelGroupPriceVM {
  /** 分组展示名（如「经济组」） */
  name: string;
  /** 分组编码 */
  code: string;
  /** 分组售价倍率（售价 = 基准价 × 本倍率） */
  ratio: number;
  /** 该分组下的实际售价（USD/1M）；基准价未知时 null */
  price: number | null;
}

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
  /** 基准价（USD/1M，base_price_ratio）；0 或缺失表示"以控制台为准" */
  basePrice: number | null;
  /** 该模型所在的可用分组价格对比（无则空数组） */
  groups: ModelGroupPriceVM[];
  /** 各分组中的最低售价（USD/1M）；无价/无分组时回退基准价，再无则 null */
  fromPrice: number | null;
  /** 缓存命中价倍率（cache_ratio），可选展示 */
  cacheRatio: number | null;
  /** 平台为该对外模型配置的描述（来自 public_models.description，经 /api/pricing 透传）；无则空串 */
  description: string;
}

/**
 * DTO（PricingModelEntry）+ 静态展示目录 → 卡片视图模型。
 * 显式白名单：只挑允许字段，杜绝敏感字段透传到 UI。
 */
export function toModelCardVM(entry: PricingModelEntry): ModelCardVM {
  const modelName = entry.model_name ?? '';
  // 按对外名解析展示元数据（厂商/上下文/能力/官方价）。
  const meta = resolveCatalogMeta(modelName);
  // base_price_ratio 是「倍率」，非价格。实际展示价 = 官方基础价 × 倍率。
  const ratio = typeof entry.base_price_ratio === 'number' ? entry.base_price_ratio : null;
  const official = meta.officialPrice && meta.officialPrice > 0 ? meta.officialPrice : null;
  const basePrice = official != null && ratio != null && ratio > 0 ? official * ratio : null;
  const cats = meta.cats;

  // 分组价格对比：每个可见分组的售价 = 基准价 × 分组倍率（零泄露，只用公开倍率）。
  const groups: ModelGroupPriceVM[] = (entry.groups ?? []).map((g) => {
    const gRatio = typeof g.ratio === 'number' ? g.ratio : 1;
    return {
      name: g.name ?? '',
      code: g.code ?? '',
      ratio: gRatio,
      price: basePrice != null ? basePrice * gRatio : null,
    };
  });

  // 最低价：分组售价取最小；无分组价则回退基准价。
  const groupPrices = groups.map((g) => g.price).filter((p): p is number => p != null);
  const fromPrice = groupPrices.length > 0 ? Math.min(...groupPrices) : basePrice;

  return {
    modelName,
    displayName: entry.display_name || modelName,
    vendor: meta.vendor,
    ctx: meta.ctx,
    cats,
    capabilityText: cats.map((c) => CAPABILITY_LABEL[c] ?? c).join(' · '),
    tags: meta.tags,
    basePrice,
    groups,
    fromPrice,
    cacheRatio: typeof entry.cache_ratio === 'number' ? entry.cache_ratio : null,
    // 描述：catalog 内置优先，回落后端 public_models.description（schema 未含该字段时安全可选读取）。
    description: (meta.desc || (entry as { description?: string }).description || '').trim(),
  };
}

/** 把 PricingPublicView 整体映射成卡片 VM 列表。 */
export function toModelCardVMs(view: PricingPublicView): ModelCardVM[] {
  return (view.models ?? []).map(toModelCardVM);
}

/** 模型名 A → 展示元信息（厂商/上下文/能力）。未命中回退 FALLBACK_META。 */
export interface ModelDisplayMeta {
  vendor: string;
  ctx: string;
}

/**
 * 由对外模型名 A 解析展示元信息（厂商 + 上下文体量）。
 * 跨域消费（如排行榜按名补厂商）经此函数，不深 import catalog 常量。
 */
export function modelDisplayMeta(modelName: string): ModelDisplayMeta {
  const meta = resolveCatalogMeta(modelName);
  return { vendor: meta.vendor, ctx: meta.ctx };
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
