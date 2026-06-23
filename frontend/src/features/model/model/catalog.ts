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
  /** 官方基础价（USD / 1M tokens，对外展示价 = 官方价 × base_price_ratio）。0/缺失=未知 */
  officialPrice?: number;
  /** 内置中文描述（广场卡片/详情简介展示，可被后端 description 覆盖/回落） */
  desc?: string;
  /** 同族归组键（同模型多品质档共享，可选） */
  family?: string;
  /** 同族展示名（可选） */
  familyLabel?: string;
}

/** model_name → 展示元数据。键与 mock-pricing / 真实 /api/pricing 的 model_name 对齐。 */
export const MODEL_CATALOG: Record<string, ModelCatalogMeta> = {
  /* ── OpenAI ── */
  'gpt-5': { vendor: 'OpenAI', ctx: '256K', cats: ['chat', 'vision', 'code', 'reasoning'], tags: ['对话', '推理', '多模态', '旗舰'], officialPrice: 1.25, desc: 'OpenAI 最新一代旗舰，统一对话与推理，复杂任务、代码与多模态能力全面领先。' },
  'gpt-5-mini': { vendor: 'OpenAI', ctx: '256K', cats: ['chat', 'vision', 'code'], tags: ['对话', '轻量', '高性价比'], officialPrice: 0.25, desc: 'GPT-5 轻量版，速度快、成本低，适合高并发与日常任务。' },
  'gpt-4o': { vendor: 'OpenAI', ctx: '128K', cats: ['chat', 'vision'], tags: ['对话', '多模态', '函数调用'], officialPrice: 2.5, desc: 'OpenAI 多模态旗舰，均衡的对话、视觉与函数调用能力，适合大多数生产场景。' },
  'gpt-4o-mini': { vendor: 'OpenAI', ctx: '128K', cats: ['chat', 'vision'], tags: ['对话', '轻量', '高性价比'], officialPrice: 0.15, desc: 'GPT-4o 的轻量版，成本极低、速度快，适合高并发与日常任务。' },
  'gpt-4.1': { vendor: 'OpenAI', ctx: '1M', cats: ['chat', 'vision', 'code'], tags: ['对话', '代码', '长上下文'], officialPrice: 2, desc: 'GPT-4.1，超长上下文（1M），指令遵循与代码能力显著增强。' },
  'gpt-4.1-mini': { vendor: 'OpenAI', ctx: '1M', cats: ['chat', 'vision', 'code'], tags: ['对话', '轻量', '长上下文'], officialPrice: 0.4, desc: 'GPT-4.1 轻量版，长上下文与高性价比兼具。' },
  o1: { vendor: 'OpenAI', ctx: '200K', cats: ['reasoning'], tags: ['推理', '复杂任务'], officialPrice: 15, desc: 'OpenAI 推理模型，擅长数学、代码与多步复杂推理。' },
  'o1-mini': { vendor: 'OpenAI', ctx: '128K', cats: ['reasoning'], tags: ['推理', '轻量'], officialPrice: 3, desc: 'o1 的轻量推理版，性价比更高，适合中等难度推理。' },
  o3: { vendor: 'OpenAI', ctx: '200K', cats: ['reasoning', 'code'], tags: ['推理', '代码', '复杂任务'], officialPrice: 2, desc: 'OpenAI o3 推理模型，数学、科学与代码推理能力顶尖。' },
  'o3-mini': { vendor: 'OpenAI', ctx: '200K', cats: ['reasoning'], tags: ['推理', '轻量', '高性价比'], officialPrice: 1.1, desc: '轻量推理模型，推理质量与成本兼顾。' },
  'o4-mini': { vendor: 'OpenAI', ctx: '200K', cats: ['reasoning', 'vision'], tags: ['推理', '多模态', '轻量'], officialPrice: 1.1, desc: 'o4-mini，支持视觉的高性价比推理模型，适合大规模推理场景。' },

  /* ── Anthropic ── */
  'claude-opus-4.1': { vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'vision', 'code', 'reasoning'], tags: ['对话', '代码', '推理', '旗舰'], officialPrice: 15, desc: 'Claude Opus 4.1，Anthropic 最强模型，复杂 Agent、深度推理与长文本顶级表现。' },
  'claude-sonnet-4.5': { vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'vision', 'code'], tags: ['对话', '代码', '多模态', '旗舰'], officialPrice: 3, desc: 'Claude Sonnet 4.5，新一代均衡旗舰，代码与 Agent 能力大幅提升，综合性价比之王。', family: 'claude-sonnet-4.5', familyLabel: 'Claude Sonnet 4.5' },
  'claude-haiku-4.5': { vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'code'], tags: ['对话', '轻量', '快速'], officialPrice: 1, desc: 'Claude Haiku 4.5，高速轻量版，成本低、响应快，适合高频与实时场景。', family: 'claude-haiku-4.5', familyLabel: 'Claude Haiku 4.5' },
  'claude-3.7-sonnet': { vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'vision', 'code', 'reasoning'], tags: ['对话', '代码', '推理'], officialPrice: 3, desc: 'Claude 3.7 Sonnet，首款混合推理模型，可按需开启深度思考。' },
  'claude-3.5-sonnet': { vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'vision', 'code'], tags: ['对话', '代码', '多模态'], officialPrice: 3, desc: 'Claude 3.5 Sonnet，代码与长文本表现优异，综合能力强。' },
  'claude-3.5-haiku': { vendor: 'Anthropic', ctx: '200K', cats: ['chat'], tags: ['对话', '轻量', '快速'], officialPrice: 0.8, desc: 'Claude 3.5 轻量快速版，低延迟、低成本，适合实时与高频调用。' },
  'claude-3-opus': { vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'vision'], tags: ['对话', '多模态', '高质量'], officialPrice: 15, desc: '上一代 Claude 旗舰，深度推理与高质量长文本生成。' },
  'opus-4.8': { vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'vision', 'code', 'reasoning'], tags: ['对话', '代码', '推理', '旗舰'], officialPrice: 15, desc: 'Claude Opus 4.8，旗舰中的旗舰，复杂推理与超长文本的顶级表现。', family: 'opus-4.8', familyLabel: 'Claude Opus 4.8' },

  /* ── Google ── */
  'gemini-2.5-pro': { vendor: 'Google', ctx: '1M', cats: ['chat', 'vision', 'code', 'reasoning'], tags: ['对话', '多模态', '推理', '长上下文'], officialPrice: 1.25, desc: 'Gemini 2.5 Pro，Google 旗舰，超长上下文（1M）+ 原生多模态 + 深度思考。' },
  'gemini-2.5-flash': { vendor: 'Google', ctx: '1M', cats: ['chat', 'vision', 'code'], tags: ['对话', '多模态', '快速', '高性价比'], officialPrice: 0.3, desc: 'Gemini 2.5 Flash，速度与成本最优，支持长上下文与多模态。' },
  'gemini-2.0-flash': { vendor: 'Google', ctx: '1M', cats: ['chat', 'vision'], tags: ['对话', '多模态', '快速'], officialPrice: 0.1, desc: 'Gemini 2.0 Flash，高速多模态模型，超低成本。' },
  'gemini-1.5-pro': { vendor: 'Google', ctx: '2M', cats: ['chat', 'vision'], tags: ['对话', '多模态', '长上下文'], officialPrice: 1.25, desc: 'Gemini 1.5 Pro，超长上下文（2M）与多模态理解。' },
  'gemini-1.5-flash': { vendor: 'Google', ctx: '1M', cats: ['chat', 'vision'], tags: ['对话', '多模态', '高性价比'], officialPrice: 0.075, desc: 'Gemini 1.5 Flash，超低成本与长上下文兼具。' },

  /* ── xAI ── */
  'grok-4': { vendor: 'xAI', ctx: '256K', cats: ['chat', 'vision', 'code', 'reasoning'], tags: ['对话', '推理', '实时知识', '旗舰'], officialPrice: 3, desc: 'xAI Grok 4，最新旗舰，原生推理 + 实时联网知识，复杂任务表现强劲。' },
  'grok-3': { vendor: 'xAI', ctx: '131K', cats: ['chat', 'code'], tags: ['对话', '代码', '实时知识'], officialPrice: 3, desc: 'Grok 3，对话与代码能力强，具备实时信息获取。' },
  'grok-2': { vendor: 'xAI', ctx: '128K', cats: ['chat'], tags: ['对话', '实时知识'], officialPrice: 2, desc: 'Grok 2，xAI 对话模型，具备实时知识获取能力。' },

  /* ── DeepSeek ── */
  'deepseek-v3.2': { vendor: 'DeepSeek', ctx: '128K', cats: ['chat', 'code'], tags: ['对话', '代码', '开源', '高性价比'], officialPrice: 0.28, desc: 'DeepSeek V3.2，新一代开源高性价比模型，长上下文与代码能力提升。', family: 'deepseek-v3.2', familyLabel: 'DeepSeek V3.2' },
  'deepseek-3.2': { vendor: 'DeepSeek', ctx: '128K', cats: ['chat', 'code'], tags: ['对话', '代码', '高性价比'], officialPrice: 0.28, desc: 'DeepSeek 3.2，高性价比模型，长上下文与代码能力均衡。', family: 'deepseek-3.2', familyLabel: 'DeepSeek 3.2' },
  'deepseek-v3 (deepseek-chat)': { vendor: 'DeepSeek', ctx: '64K', cats: ['chat', 'code'], tags: ['对话', '代码', '开源', '高性价比'], officialPrice: 0.28, desc: 'DeepSeek V3，开源高性价比对话模型，代码能力强。' },
  'deepseek-chat': { vendor: 'DeepSeek', ctx: '128K', cats: ['chat', 'code'], tags: ['对话', '代码', '开源', '高性价比'], officialPrice: 0.28, desc: 'DeepSeek Chat，开源对话主力，中英文与代码均衡，性价比极高。' },
  'deepseek-r1 (deepseek-reasoner)': { vendor: 'DeepSeek', ctx: '64K', cats: ['reasoning'], tags: ['推理', '开源', '高性价比'], officialPrice: 0.55, desc: 'DeepSeek R1，开源推理模型，数学与逻辑表现突出。' },
  'deepseek-reasoner': { vendor: 'DeepSeek', ctx: '128K', cats: ['reasoning', 'code'], tags: ['推理', '代码', '开源'], officialPrice: 0.55, desc: 'DeepSeek Reasoner，开源推理模型，数学、代码与逻辑推理强。' },

  /* ── 阿里通义 Qwen ── */
  'qwen3-max': { vendor: '阿里通义', ctx: '256K', cats: ['chat', 'code', 'reasoning'], tags: ['对话', '中文', '推理', '旗舰'], officialPrice: 1.6, desc: '通义千问 Qwen3 Max，阿里旗舰，中英文、代码与推理全面，支持超长上下文。' },
  'qwen-max': { vendor: '阿里通义', ctx: '32K', cats: ['chat'], tags: ['对话', '中文', '阿里云'], officialPrice: 1.6, desc: '通义千问 Max，中文理解与生成能力出色。' },
  'qwen-plus': { vendor: '阿里通义', ctx: '128K', cats: ['chat', 'code'], tags: ['对话', '中文', '高性价比'], officialPrice: 0.4, desc: '通义千问 Plus，能力与成本均衡的中文主力模型。' },
  'qwen-turbo': { vendor: '阿里通义', ctx: '1M', cats: ['chat'], tags: ['对话', '中文', '快速', '长上下文'], officialPrice: 0.05, desc: '通义千问 Turbo，超低成本高速模型，支持百万长上下文。' },

  /* ── 智谱 GLM ── */
  'glm-4.6': { vendor: '智谱AI', ctx: '200K', cats: ['chat', 'code', 'reasoning'], tags: ['对话', '中文', '代码', '旗舰'], officialPrice: 0.6, desc: '智谱 GLM-4.6，新一代旗舰，代码、推理与 Agent 能力强，国产高性价比之选。' },
  'glm-4.5': { vendor: '智谱AI', ctx: '128K', cats: ['chat', 'code', 'reasoning'], tags: ['对话', '中文', '代码'], officialPrice: 0.6, desc: '智谱 GLM-4.5，混合推理模型，兼顾对话、代码与深度思考。' },
  'glm-4-plus': { vendor: '智谱AI', ctx: '128K', cats: ['chat'], tags: ['对话', '中文'], officialPrice: 0.7, desc: '智谱 GLM-4 Plus，中文对话与工具调用能力强。' },

  /* ── 月之暗面 Kimi ── */
  'kimi-k2': { vendor: '月之暗面', ctx: '256K', cats: ['chat', 'code', 'reasoning'], tags: ['对话', '中文', '代码', '旗舰'], officialPrice: 0.6, desc: 'Kimi K2，月之暗面旗舰 MoE 模型，超长上下文、代码与 Agent 能力突出。' },
  'moonshot-v1-128k': { vendor: '月之暗面', ctx: '128K', cats: ['chat'], tags: ['对话', '中文', '长上下文'], officialPrice: 1.7, desc: 'Kimi（月之暗面）v1，超长上下文中文模型。' },

  /* ── 字节豆包 / 百度文心 / 腾讯混元 / MiniMax ── */
  'doubao-pro': { vendor: '字节跳动', ctx: '256K', cats: ['chat', 'code'], tags: ['对话', '中文', '高性价比'], officialPrice: 0.11, desc: '豆包 Pro，字节跳动主力模型，中文与多场景表现稳定，成本极低。' },
  'ernie-4.5': { vendor: '百度', ctx: '128K', cats: ['chat'], tags: ['对话', '中文'], officialPrice: 0.55, desc: '文心一言 4.5，百度旗舰，中文知识与生成能力强。' },
  'hunyuan-turbo': { vendor: '腾讯', ctx: '256K', cats: ['chat'], tags: ['对话', '中文'], officialPrice: 0.4, desc: '腾讯混元 Turbo，中文对话与多场景应用模型。' },
  'abab6.5s': { vendor: 'MiniMax', ctx: '245K', cats: ['chat'], tags: ['对话', '中文', '长上下文'], officialPrice: 0.14, desc: 'MiniMax abab6.5s，长上下文中文对话模型，速度快。' },

  /* ── Meta / Mistral ── */
  'llama-4-maverick': { vendor: 'Meta', ctx: '1M', cats: ['chat', 'vision', 'code'], tags: ['对话', '多模态', '开源', '长上下文'], officialPrice: 0.6, desc: 'Llama 4 Maverick，Meta 开源 MoE 旗舰，原生多模态 + 超长上下文，可私有部署。' },
  'llama-3.3-70b': { vendor: 'Meta', ctx: '128K', cats: ['chat', 'code'], tags: ['对话', '代码', '开源'], officialPrice: 0.6, desc: 'Llama 3.3 70B，Meta 开源旗舰，可私有部署。' },
  'mistral-large': { vendor: 'Mistral', ctx: '128K', cats: ['chat', 'code'], tags: ['对话', '代码', '欧洲'], officialPrice: 2, desc: 'Mistral Large，欧洲团队出品，代码与多语言能力均衡。' },
};

/** 兜底展示元数据（未命中目录时，避免渲染崩） */
export const FALLBACK_META: ModelCatalogMeta = {
  vendor: '—',
  ctx: '—',
  cats: ['chat'],
  tags: [],
};

/**
 * 按对外名 A 解析展示元数据：先精确命中，否则剥掉品质后缀（-air/-max/-pro，及历史中文 -经济/-增强/-旗舰）
 * 用底层模型名命中，再否则回退 FALLBACK_META。
 *
 * <p>这样发布出来的品质分级商品（如 claude-sonnet-4.5-air）能继承底层模型（claude-sonnet-4.5）
 * 的官方价/描述/厂商等展示元信息，无需为每个品质档单独登记。</p>
 */
export function resolveCatalogMeta(modelName: string): ModelCatalogMeta {
  if (!modelName) return FALLBACK_META;
  const exact = MODEL_CATALOG[modelName];
  if (exact) return exact;
  const base = modelName.replace(/-(air|max|pro|经济|增强|旗舰)$/i, '');
  if (base !== modelName && MODEL_CATALOG[base]) {
    return MODEL_CATALOG[base];
  }
  return FALLBACK_META;
}

/** 能力中文名映射。 */
export const CAPABILITY_LABEL: Record<ModelCapability, string> = {
  chat: '对话',
  reasoning: '推理',
  vision: '多模态',
  code: '代码',
};
