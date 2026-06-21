/**
 * shared/api/pricing.types — 从 openapi 生成的 schema.ts 提取的价格/模型 DTO 别名。
 *
 * 不手维护接口类型：契约变了 `npm run gen:api` 重新生成 schema.ts 即可。
 * 本文件只做命名收敛，便于 features/billing、features/model 引用。
 */
import type { components } from './schema';

type Schemas = components['schemas'];

/**
 * 公开模型价格页视图（客户/公开视图）。
 * 契约 PricingPublicView 绝不含 B/成本/供应商——零泄露在契约层已裁定。
 */
export type PricingPublicView = Schemas['PricingPublicView'];

/** 单个公开模型价格条目（PricingPublicView.models 数组元素）。 */
export type PricingModelEntry = NonNullable<PricingPublicView['models']>[number];
