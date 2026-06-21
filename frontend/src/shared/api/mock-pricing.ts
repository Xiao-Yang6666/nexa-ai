/**
 * shared/api/mock-pricing — 开发期 /api/pricing 桩数据。
 *
 * 严格对齐 openapi PricingPublicView（components.schemas.PricingPublicView）：
 * models[] 仅含 { model_name, base_price_ratio, quality_tier, display_name,
 * supported_endpoint, cache_ratio }，外加 group_ratio / auto_groups / pricing_version。
 *
 * 客户端零泄露（产品铁律）：本桩绝不含成本(quota_cost)/利润(quota_profit)/
 * 上游真实模型 B(upstream_name)/供应商——契约 PublicView 本就裁掉了这些，桩亦不臆造。
 *
 * 数据取材自 06_prototype/final/assets/model-data.js 的对外名 A 与品质档，
 * base_price_ratio 为「基准价（折扣=1 口径）」的相对倍率（越低越便宜），仅作开发期展示。
 */
import type { PricingPublicView } from './pricing.types';

export const MOCK_PRICING: PricingPublicView = {
  pricing_version: '2026-06-20',
  auto_groups: ['default', 'vip'],
  group_ratio: {
    default: 1,
    vip: 0.85,
  },
  models: [
    // OpenAI
    { model_name: 'gpt-4o', display_name: 'GPT-4o', quality_tier: 'full', base_price_ratio: 2.5, supported_endpoint: 'chat', cache_ratio: 0.5 },
    { model_name: 'gpt-4o-mini', display_name: 'GPT-4o mini', quality_tier: 'air', base_price_ratio: 0.15, supported_endpoint: 'chat', cache_ratio: 0.5 },
    { model_name: 'o1', display_name: 'OpenAI o1', quality_tier: 'full', base_price_ratio: 15, supported_endpoint: 'chat', cache_ratio: 0.5 },
    { model_name: 'o1-mini', display_name: 'OpenAI o1-mini', quality_tier: 'air', base_price_ratio: 1.1, supported_endpoint: 'chat', cache_ratio: 0.5 },
    { model_name: 'o3-mini', display_name: 'OpenAI o3-mini', quality_tier: 'air', base_price_ratio: 1.1, supported_endpoint: 'chat', cache_ratio: 0.5 },
    // Anthropic
    { model_name: 'claude-3.5-sonnet', display_name: 'Claude 3.5 Sonnet', quality_tier: 'full', base_price_ratio: 3, supported_endpoint: 'chat', cache_ratio: 0.1 },
    { model_name: 'claude-3.5-haiku', display_name: 'Claude 3.5 Haiku', quality_tier: 'air', base_price_ratio: 0.8, supported_endpoint: 'chat', cache_ratio: 0.1 },
    { model_name: 'claude-3-opus', display_name: 'Claude 3 Opus', quality_tier: 'full', base_price_ratio: 15, supported_endpoint: 'chat', cache_ratio: 0.1 },
    // opus-4.8 家族（同模型三品质档样板）
    { model_name: 'opus-4.8', display_name: 'Claude Opus 4.8 · 旗舰', quality_tier: 'full', base_price_ratio: 15, supported_endpoint: 'chat', cache_ratio: 0.1 },
    { model_name: 'opus-4.8-增强', display_name: 'Claude Opus 4.8 · 增强', quality_tier: 'max', base_price_ratio: 9, supported_endpoint: 'chat', cache_ratio: 0.1 },
    { model_name: 'opus-4.8-经济', display_name: 'Claude Opus 4.8 · 经济', quality_tier: 'air', base_price_ratio: 3, supported_endpoint: 'chat', cache_ratio: 0.1 },
    // Google
    { model_name: 'gemini-1.5-pro', display_name: 'Gemini 1.5 Pro', quality_tier: 'full', base_price_ratio: 1.25, supported_endpoint: 'chat', cache_ratio: 0.25 },
    { model_name: 'gemini-1.5-flash', display_name: 'Gemini 1.5 Flash', quality_tier: 'air', base_price_ratio: 0.075, supported_endpoint: 'chat', cache_ratio: 0.25 },
    { model_name: 'gemini-2.0-flash', display_name: 'Gemini 2.0 Flash', quality_tier: 'air', base_price_ratio: 0.1, supported_endpoint: 'chat', cache_ratio: 0.25 },
    // xAI
    { model_name: 'grok-2', display_name: 'Grok 2', quality_tier: 'full', base_price_ratio: 2, supported_endpoint: 'chat', cache_ratio: 1 },
    { model_name: 'grok-beta', display_name: 'Grok beta', quality_tier: 'max', base_price_ratio: 5, supported_endpoint: 'chat', cache_ratio: 1 },
    // DeepSeek
    { model_name: 'deepseek-v3 (deepseek-chat)', display_name: 'DeepSeek V3', quality_tier: 'air', base_price_ratio: 0.2, supported_endpoint: 'chat', cache_ratio: 0.1 },
    { model_name: 'deepseek-r1 (deepseek-reasoner)', display_name: 'DeepSeek R1', quality_tier: 'max', base_price_ratio: 0.7, supported_endpoint: 'chat', cache_ratio: 0.1 },
    // Mistral
    { model_name: 'mistral-large', display_name: 'Mistral Large', quality_tier: 'full', base_price_ratio: 2, supported_endpoint: 'chat', cache_ratio: 1 },
    // Meta
    { model_name: 'llama-3.3-70b', display_name: 'Llama 3.3 70B', quality_tier: 'air', base_price_ratio: 0.1, supported_endpoint: 'chat', cache_ratio: 1 },
    // 阿里通义
    { model_name: 'qwen-max', display_name: 'Qwen Max', quality_tier: 'full', base_price_ratio: 1.6, supported_endpoint: 'chat', cache_ratio: 1 },
    // 智谱 / 月之暗面（base_price_ratio 待核场景：给 0 表示以控制台为准）
    { model_name: 'glm-4-plus', display_name: 'GLM-4 Plus', quality_tier: 'full', base_price_ratio: 0, supported_endpoint: 'chat', cache_ratio: 1 },
    { model_name: 'moonshot-v1-128k', display_name: 'Moonshot v1 128K', quality_tier: 'full', base_price_ratio: 0, supported_endpoint: 'chat', cache_ratio: 1 },
  ],
};
