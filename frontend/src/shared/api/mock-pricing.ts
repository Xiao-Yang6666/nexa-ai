/**
 * shared/api/mock-pricing — 开发期 /api/pricing 桩数据。
 *
 * 严格对齐 openapi PricingPublicView（components.schemas.PricingPublicView）：
 * models[] 仅含 { model_name, base_price_ratio, display_name, description,
 * groups[], supported_endpoint, cache_ratio }，外加 group_ratio / auto_groups / pricing_version。
 *
 * 客户端零泄露（产品铁律）：本桩绝不含成本(quota_cost)/利润(quota_profit)/
 * 上游真实模型 B(upstream_name)/供应商——契约 PublicView 本就裁掉了这些，桩亦不臆造。
 *
 * 定价模型（重设计后）：一个真实模型一条记录（不再按品质档拆条）；差异定价由「价格分组」承载——
 * 同一模型加入多个分组、各组设不同倍率，groups[] 即该模型在各可见分组里的价格对比。
 * base_price_ratio 为「基准价（折扣=1 口径）」；分组售价 = base_price_ratio × group.ratio。
 */
import type { PricingPublicView } from './pricing.types';

export const MOCK_PRICING: PricingPublicView = {
  pricing_version: '2026-06-29',
  auto_groups: ['default', 'vip'],
  group_ratio: {
    default: 1,
    vip: 0.85,
  },
  models: [
    // OpenAI
    { model_name: 'gpt-4o', display_name: 'GPT-4o', base_price_ratio: 2.5, groups: [{ name: '经济组', code: 'eco', ratio: 0.5 }, { name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 0.5 },
    { model_name: 'gpt-4o-mini', display_name: 'GPT-4o mini', base_price_ratio: 0.15, groups: [{ name: '经济组', code: 'eco', ratio: 0.5 }, { name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 0.5 },
    { model_name: 'o1', display_name: 'OpenAI o1', base_price_ratio: 15, groups: [{ name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 0.5 },
    { model_name: 'o1-mini', display_name: 'OpenAI o1-mini', base_price_ratio: 1.1, groups: [{ name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 0.5 },
    { model_name: 'o3-mini', display_name: 'OpenAI o3-mini', base_price_ratio: 1.1, groups: [{ name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 0.5 },
    // Anthropic
    { model_name: 'claude-3.5-sonnet', display_name: 'Claude 3.5 Sonnet', base_price_ratio: 3, groups: [{ name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 0.1 },
    { model_name: 'claude-3.5-haiku', display_name: 'Claude 3.5 Haiku', base_price_ratio: 0.8, groups: [{ name: '经济组', code: 'eco', ratio: 0.5 }, { name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 0.1 },
    { model_name: 'claude-3-opus', display_name: 'Claude 3 Opus', base_price_ratio: 15, groups: [{ name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 0.1 },
    // opus-4.8（一条记录，多分组价格对比样板：经济 ×0.5 / 标准 ×1 / 旗舰 ×2）
    { model_name: 'opus-4.8', display_name: 'Claude Opus 4.8', base_price_ratio: 15, groups: [{ name: '经济组', code: 'eco', ratio: 0.5 }, { name: '标准组', code: 'std', ratio: 1 }, { name: '旗舰组', code: 'flagship', ratio: 2 }], supported_endpoint: 'chat', cache_ratio: 0.1 },
    // Google
    { model_name: 'gemini-1.5-pro', display_name: 'Gemini 1.5 Pro', base_price_ratio: 1.25, groups: [{ name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 0.25 },
    { model_name: 'gemini-1.5-flash', display_name: 'Gemini 1.5 Flash', base_price_ratio: 0.075, groups: [{ name: '经济组', code: 'eco', ratio: 0.5 }, { name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 0.25 },
    { model_name: 'gemini-2.0-flash', display_name: 'Gemini 2.0 Flash', base_price_ratio: 0.1, groups: [{ name: '经济组', code: 'eco', ratio: 0.5 }, { name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 0.25 },
    // xAI
    { model_name: 'grok-2', display_name: 'Grok 2', base_price_ratio: 2, groups: [{ name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 1 },
    { model_name: 'grok-beta', display_name: 'Grok beta', base_price_ratio: 5, groups: [{ name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 1 },
    // DeepSeek
    { model_name: 'deepseek-v3 (deepseek-chat)', display_name: 'DeepSeek V3', base_price_ratio: 0.2, groups: [{ name: '经济组', code: 'eco', ratio: 0.5 }, { name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 0.1 },
    { model_name: 'deepseek-r1 (deepseek-reasoner)', display_name: 'DeepSeek R1', base_price_ratio: 0.7, groups: [{ name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 0.1 },
    // Mistral
    { model_name: 'mistral-large', display_name: 'Mistral Large', base_price_ratio: 2, groups: [{ name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 1 },
    // Meta
    { model_name: 'llama-3.3-70b', display_name: 'Llama 3.3 70B', base_price_ratio: 0.1, groups: [{ name: '经济组', code: 'eco', ratio: 0.5 }, { name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 1 },
    // 阿里通义
    { model_name: 'qwen-max', display_name: 'Qwen Max', base_price_ratio: 1.6, groups: [{ name: '标准组', code: 'std', ratio: 1 }], supported_endpoint: 'chat', cache_ratio: 1 },
    // 智谱 / 月之暗面（base_price_ratio 待核场景：给 0 表示以控制台为准）
    { model_name: 'glm-4-plus', display_name: 'GLM-4 Plus', base_price_ratio: 0, groups: [], supported_endpoint: 'chat', cache_ratio: 1 },
    { model_name: 'moonshot-v1-128k', display_name: 'Moonshot v1 128K', base_price_ratio: 0, groups: [], supported_endpoint: 'chat', cache_ratio: 1 },
  ],
};
