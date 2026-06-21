/**
 * features/model/model/catalog — 模型广场展示元数据（厂商/上下文/能力/标签）。
 *
 * 说明：openapi /api/pricing(PricingPublicView) 只下发价格相关字段
 * （model_name/base_price_ratio/quality_tier/display_name/supported_endpoint/cache_ratio），
 * 不含厂商/上下文/能力标签这类「商品展示元数据」。这些在真实系统由模型元数据接口
 * （ModelMeta / 供应商目录）提供。开发期以此静态目录按 model_name 关联补全展示信息，
 * 与 /api/pricing 的价格数据合并成模型广场卡片视图。
 *
 * 客户端零泄露：本目录只含对外可见的展示信息（厂商品牌名属公开信息，
 * 非"上游真实模型 B"也非供应商渠道），绝不含成本/利润/B 映射。
 */

/** 能力分类（与原型 cats 对齐）。 */
export type ModelCapability = 'chat' | 'reasoning' | 'vision' | 'code';

export interface ModelCatalogMeta {
  /** 厂商对外品牌名（公开信息，用于头像/分组展示） */
  vendor: string;
  /** 上下文长度展示串（如 '128K' / '2M'） */
  ctx: string;
  /** 能力分类（驱动筛选 pill） */
  cats: ModelCapability[];
  /** 展示标签（中文，最多取前 3 个上卡） */
  tags: string[];
  /** 同族归组键（同模型多品质档共享，可选） */
  family?: string;
  /** 同族展示名（可选） */
  familyLabel?: string;
}

/** model_name → 展示元数据。键与 mock-pricing / 真实 /api/pricing 的 model_name 对齐。 */
export const MODEL_CATALOG: Record<string, ModelCatalogMeta> = {
  'gpt-4o': { vendor: 'OpenAI', ctx: '128K', cats: ['chat', 'vision'], tags: ['对话', '多模态', '函数调用'] },
  'gpt-4o-mini': { vendor: 'OpenAI', ctx: '128K', cats: ['chat', 'vision'], tags: ['对话', '轻量', '高性价比'] },
  o1: { vendor: 'OpenAI', ctx: '200K', cats: ['reasoning'], tags: ['推理', '复杂任务'] },
  'o1-mini': { vendor: 'OpenAI', ctx: '128K', cats: ['reasoning'], tags: ['推理', '轻量'] },
  'o3-mini': { vendor: 'OpenAI', ctx: '200K', cats: ['reasoning'], tags: ['推理', '轻量', '高性价比'] },
  'claude-3.5-sonnet': { vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'vision', 'code'], tags: ['对话', '代码', '多模态'] },
  'claude-3.5-haiku': { vendor: 'Anthropic', ctx: '200K', cats: ['chat'], tags: ['对话', '轻量', '快速'] },
  'claude-3-opus': { vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'vision'], tags: ['对话', '多模态', '高质量'] },
  'opus-4.8': { vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'vision', 'code'], tags: ['对话', '代码', '多模态', '旗舰'], family: 'opus-4.8', familyLabel: 'Claude Opus 4.8' },
  'opus-4.8-增强': { vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'vision', 'code'], tags: ['对话', '代码', '高性价比'], family: 'opus-4.8', familyLabel: 'Claude Opus 4.8' },
  'opus-4.8-经济': { vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'code'], tags: ['对话', '入门', '低价'], family: 'opus-4.8', familyLabel: 'Claude Opus 4.8' },
  'gemini-1.5-pro': { vendor: 'Google', ctx: '2M', cats: ['chat', 'vision'], tags: ['对话', '多模态', '长上下文'] },
  'gemini-1.5-flash': { vendor: 'Google', ctx: '1M', cats: ['chat', 'vision'], tags: ['对话', '多模态', '高性价比'] },
  'gemini-2.0-flash': { vendor: 'Google', ctx: '1M', cats: ['chat', 'vision'], tags: ['对话', '多模态', '快速'] },
  'grok-2': { vendor: 'xAI', ctx: '128K', cats: ['chat'], tags: ['对话', '实时知识'] },
  'grok-beta': { vendor: 'xAI', ctx: '128K', cats: ['chat'], tags: ['对话', '测试版'] },
  'deepseek-v3 (deepseek-chat)': { vendor: 'DeepSeek', ctx: '64K', cats: ['chat', 'code'], tags: ['对话', '代码', '开源', '高性价比'] },
  'deepseek-r1 (deepseek-reasoner)': { vendor: 'DeepSeek', ctx: '64K', cats: ['reasoning'], tags: ['推理', '开源', '高性价比'] },
  'mistral-large': { vendor: 'Mistral', ctx: '128K', cats: ['chat', 'code'], tags: ['对话', '代码', '欧洲'] },
  'llama-3.3-70b': { vendor: 'Meta', ctx: '128K', cats: ['chat', 'code'], tags: ['对话', '代码', '开源'] },
  'qwen-max': { vendor: '阿里通义', ctx: '32K', cats: ['chat'], tags: ['对话', '中文', '阿里云'] },
  'glm-4-plus': { vendor: '智谱AI', ctx: '128K', cats: ['chat'], tags: ['对话', '中文'] },
  'moonshot-v1-128k': { vendor: '月之暗面', ctx: '128K', cats: ['chat'], tags: ['对话', '中文', '长上下文'] },
};

/** 兜底展示元数据（未命中目录时，避免渲染崩） */
export const FALLBACK_META: ModelCatalogMeta = {
  vendor: '—',
  ctx: '—',
  cats: ['chat'],
  tags: [],
};

/** 能力中文名映射。 */
export const CAPABILITY_LABEL: Record<ModelCapability, string> = {
  chat: '对话',
  reasoning: '推理',
  vision: '多模态',
  code: '代码',
};
