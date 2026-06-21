/**
 * features/ranking/model/ranking-data — 排行榜静态展示数据（typed）。
 *
 * 从 06_prototype/final/assets/model-data.js (window.MODEL_PRICING) +
 * ranking.html 内联的 CAP/SPEED/CALLS 评估常量 1:1 工程化迁移。
 *
 * 数据性质：官方价（official_in/out，来自各厂商公开 pricing 页）是公开信息；
 * Nexa 价由「平台定价倍率」推导（平台对外定价策略，不是成本 B、不是利润、不是供应商维度）；
 * 能力/速度/调用量为产品侧合理评估展示值。整套均为「对客户公开」的展示数据，
 * 不含 quota_cost / quota_profit / 上游真实模型 B / 供应商渠道——零泄露成立。
 *
 * 排行榜是纯展示页（无对应公开榜单接口），故数据落为页面常量；
 * 价格的真实单价以控制台计费页为准（页内已注明）。
 */

/** 模型基础条目（官方价 + 元数据，公开字段）。 */
export interface RankingModelSeed {
  name: string;
  vendor: string;
  ctx: string;
  /** 官方输入价 USD/1M（公开）；null = 待核 */
  officialIn: number | null;
  /** 官方输出价 USD/1M（公开）；null = 待核 */
  officialOut: number | null;
}

/** 模型基础数据（官方价 + 上下文）。 */
export const RANKING_SEEDS: RankingModelSeed[] = [
  { name: 'gpt-4o', vendor: 'OpenAI', ctx: '128K', officialIn: 2.5, officialOut: 10.0 },
  { name: 'gpt-4o-mini', vendor: 'OpenAI', ctx: '128K', officialIn: 0.15, officialOut: 0.6 },
  { name: 'o1', vendor: 'OpenAI', ctx: '200K', officialIn: 15.0, officialOut: 60.0 },
  { name: 'o1-mini', vendor: 'OpenAI', ctx: '128K', officialIn: 1.1, officialOut: 4.4 },
  { name: 'o3-mini', vendor: 'OpenAI', ctx: '200K', officialIn: 1.1, officialOut: 4.4 },
  { name: 'claude-3.5-sonnet', vendor: 'Anthropic', ctx: '200K', officialIn: 3.0, officialOut: 15.0 },
  { name: 'claude-3.5-haiku', vendor: 'Anthropic', ctx: '200K', officialIn: 0.8, officialOut: 4.0 },
  { name: 'claude-3-opus', vendor: 'Anthropic', ctx: '200K', officialIn: 15.0, officialOut: 75.0 },
  { name: 'gemini-1.5-pro', vendor: 'Google', ctx: '2M', officialIn: 1.25, officialOut: 5.0 },
  { name: 'gemini-1.5-flash', vendor: 'Google', ctx: '1M', officialIn: 0.075, officialOut: 0.3 },
  { name: 'gemini-2.0-flash', vendor: 'Google', ctx: '1M', officialIn: 0.1, officialOut: 0.4 },
  { name: 'grok-2', vendor: 'xAI', ctx: '128K', officialIn: 2.0, officialOut: 10.0 },
  { name: 'grok-beta', vendor: 'xAI', ctx: '128K', officialIn: 5.0, officialOut: 15.0 },
  { name: 'deepseek-v3 (deepseek-chat)', vendor: 'DeepSeek', ctx: '64K', officialIn: 0.2, officialOut: 0.8 },
  { name: 'deepseek-r1 (deepseek-reasoner)', vendor: 'DeepSeek', ctx: '64K', officialIn: 0.7, officialOut: 2.5 },
  { name: 'mistral-large', vendor: 'Mistral', ctx: '128K', officialIn: 2.0, officialOut: 6.0 },
  { name: 'llama-3.3-70b', vendor: 'Meta', ctx: '128K', officialIn: 0.1, officialOut: 0.32 },
  { name: 'qwen-max', vendor: '阿里通义', ctx: '32K', officialIn: 1.6, officialOut: 6.4 },
  { name: 'glm-4-plus', vendor: '智谱AI', ctx: '128K', officialIn: null, officialOut: null },
  { name: 'moonshot-v1-128k', vendor: '月之暗面', ctx: '128K', officialIn: null, officialOut: null },
];

/** 缺价模型（待核）的稳定估算价（仅用于展示排序，与原型一致）。 */
export const EST_PRICE: Record<string, { in: number; out: number }> = {
  'glm-4-plus': { in: 0.6, out: 1.8 },
  'moonshot-v1-128k': { in: 1.7, out: 1.7 },
};

/** 能力分（0-100）：模型定位 × 官方价位综合评估值（展示用）。 */
export const CAP: Record<string, number> = {
  o1: 97,
  'claude-3-opus': 95,
  'o3-mini': 89,
  'claude-3.5-sonnet': 94,
  'gpt-4o': 92,
  'deepseek-r1 (deepseek-reasoner)': 91,
  'gemini-1.5-pro': 88,
  'grok-2': 85,
  'mistral-large': 83,
  'o1-mini': 82,
  'qwen-max': 82,
  'glm-4-plus': 80,
  'deepseek-v3 (deepseek-chat)': 86,
  'llama-3.3-70b': 79,
  'gpt-4o-mini': 74,
  'claude-3.5-haiku': 75,
  'gemini-2.0-flash': 76,
  'gemini-1.5-flash': 70,
  'grok-beta': 74,
  'moonshot-v1-128k': 76,
};

/** 生成速度（tokens/s）评估值（轻量/flash 类更快）。 */
export const SPEED: Record<string, number> = {
  'gemini-2.0-flash': 265,
  'gemini-1.5-flash': 240,
  'gpt-4o-mini': 205,
  'claude-3.5-haiku': 190,
  'llama-3.3-70b': 178,
  'gemini-1.5-pro': 150,
  'gpt-4o': 138,
  'deepseek-v3 (deepseek-chat)': 130,
  'grok-2': 122,
  'mistral-large': 118,
  'qwen-max': 115,
  'glm-4-plus': 110,
  'o3-mini': 108,
  'moonshot-v1-128k': 100,
  'claude-3.5-sonnet': 96,
  'o1-mini': 88,
  'grok-beta': 82,
  'claude-3-opus': 70,
  'deepseek-r1 (deepseek-reasoner)': 64,
  o1: 55,
};

/** 本月调用量（次）评估值（便宜 + 主流模型更高）。 */
export const CALLS: Record<string, number> = {
  'gpt-4o-mini': 1320000,
  'gpt-4o': 1180000,
  'deepseek-v3 (deepseek-chat)': 960000,
  'claude-3.5-sonnet': 910000,
  'gemini-2.0-flash': 780000,
  'gemini-1.5-flash': 640000,
  'deepseek-r1 (deepseek-reasoner)': 590000,
  'claude-3.5-haiku': 520000,
  'llama-3.3-70b': 470000,
  'o3-mini': 410000,
  'gemini-1.5-pro': 360000,
  'o1-mini': 300000,
  'qwen-max': 260000,
  'mistral-large': 220000,
  'grok-2': 180000,
  'glm-4-plus': 150000,
  o1: 120000,
  'moonshot-v1-128k': 96000,
  'claude-3-opus': 82000,
  'grok-beta': 47000,
};

/** 厂商展示色（CSS 变量 token，无裸 hex）。 */
export const VENDOR_COLOR: Record<string, string> = {
  OpenAI: 'var(--v-openai)',
  Anthropic: 'var(--v-anthropic)',
  Google: 'var(--v-google)',
  xAI: 'var(--v-xai)',
  Meta: 'var(--v-meta)',
  Mistral: 'var(--v-mistral)',
  DeepSeek: 'var(--v-deepseek)',
  阿里通义: 'var(--v-qwen)',
  智谱AI: 'var(--v-zhipu)',
  月之暗面: 'var(--v-moonshot)',
};
