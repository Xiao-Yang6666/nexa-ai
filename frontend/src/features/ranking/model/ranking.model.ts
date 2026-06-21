/**
 * features/ranking/model/ranking.model — 排行榜计算与视图模型。
 *
 * 把 ranking.html 内联的增强/归一/排序逻辑工程化为纯函数 + typed VM。
 * 计算只用公开字段（官方价 + 评估分），平台倍率为对外定价策略——零泄露。
 */
import {
  RANKING_SEEDS,
  EST_PRICE,
  CAP,
  SPEED,
  CALLS,
  type RankingModelSeed,
} from './ranking-data';

/** 榜单维度 key。 */
export type BoardId = 'overall' | 'value' | 'speed' | 'popular';

/** 单模型在各榜的视图模型（全部为公开展示字段）。 */
export interface RankingModelVM {
  name: string;
  vendor: string;
  ctx: string;
  /** 平台定价倍率（旗舰0.15/中端0.2/便宜0.3） */
  mult: number;
  /** Nexa 等权混合单价 USD/1M */
  nexaBlend: number;
  /** 官方等权混合单价 USD/1M */
  offBlend: number;
  /** 相对官方价节省百分比 */
  save: number;
  /** 能力分 0-100 */
  cap: number;
  /** 生成速度 tok/s */
  speed: number;
  /** 本月调用量（次） */
  calls: number;
  /** 性价比指数 0-100（归一） */
  valueScore: number;
  /** 综合分 0-100 */
  overall: number;
}

/**
 * 平台定价倍率分档（与 06_prototype models/pricing 口径一致）：
 * 旗舰(均价≥8)×0.15 / 便宜(mini/haiku/flash/开源 或 均价<1.2)×0.3 / 其余中端×0.2。
 * 这是对外定价策略，不读取成本 B。
 */
function tierMult(name: string, officialIn: number, officialOut: number): number {
  const n = name.toLowerCase();
  const cheapHint = /(mini|haiku|flash|nano|lite|llama|deepseek|qwen|glm|moonshot)/.test(n);
  const avg = (officialIn + officialOut) / 2;
  if (avg >= 8) return 0.15;
  if (cheapHint || avg < 1.2) return 0.3;
  return 0.2;
}

/** 解析官方价（缺价模型用稳定估算价兜底，仅用于展示排序）。 */
function resolvePrice(seed: RankingModelSeed): { in: number; out: number } {
  if (seed.officialIn != null && seed.officialOut != null) {
    return { in: seed.officialIn, out: seed.officialOut };
  }
  const est = EST_PRICE[seed.name];
  if (est) return est;
  return { in: 0, out: 0 };
}

/**
 * 构建全部模型的排行视图模型（含归一后的性价比与综合分）。
 * 纯计算、确定性输出，便于测试与 SSR。
 */
export function buildRankingModels(): RankingModelVM[] {
  const enriched = RANKING_SEEDS.map((seed) => {
    const { in: oin, out: oout } = resolvePrice(seed);
    const mult = tierMult(seed.name, oin, oout);
    const nexaIn = oin * mult;
    const nexaOut = oout * mult;
    const nexaBlend = nexaIn * 0.5 + nexaOut * 0.5;
    const offBlend = oin * 0.5 + oout * 0.5;
    const save = offBlend > 0 ? Math.round((1 - nexaBlend / offBlend) * 100) : 0;
    const cap = CAP[seed.name] ?? 75;
    const speed = SPEED[seed.name] ?? 100;
    const calls = CALLS[seed.name] ?? 50000;
    const valueRaw = cap / Math.max(nexaBlend, 0.05);
    return {
      name: seed.name,
      vendor: seed.vendor,
      ctx: seed.ctx,
      mult,
      nexaBlend,
      offBlend,
      save,
      cap,
      speed,
      calls,
      valueRaw,
    };
  });

  const maxV = Math.max(...enriched.map((x) => x.valueRaw));

  return enriched.map((x) => {
    const valueScore = Math.round((x.valueRaw / maxV) * 100);
    const overall = Math.round(x.cap * 0.85 + valueScore * 0.15);
    const { valueRaw: _drop, ...rest } = x;
    return { ...rest, valueScore, overall };
  });
}

/** 榜单配置（标题/描述/排序 key/单位/格式化）。 */
export interface BoardConfig {
  id: BoardId;
  title: string;
  desc: string;
  /** 排序 + 主指标取值 key */
  key: 'overall' | 'valueScore' | 'speed' | 'calls';
  /** 进度条归一上限 */
  max: number;
  /** 主指标标签 */
  label: string;
  /** 单位后缀 */
  unit: string;
  /** 主指标格式化 */
  fmt: (v: number) => string;
}

/** 调用量格式化（M/K）。 */
export function fmtCalls(v: number): string {
  if (v >= 1_000_000) return `${(v / 1_000_000).toFixed(1).replace(/\.0$/, '')}M`;
  if (v >= 1000) return `${Math.round(v / 1000)}K`;
  return String(v);
}

/** 价格格式化（≥1 两位小数，否则三位）。 */
export function fmtPrice(v: number): string {
  return v >= 1 ? `$${v.toFixed(2)}` : `$${v.toFixed(3)}`;
}

const identity = (v: number) => String(v);

/** 四个榜单配置（顺序即 tab 顺序）。 */
export const BOARDS: BoardConfig[] = [
  {
    id: 'overall',
    title: '综合榜',
    desc: '以能力为主、性价比为辅合成的 0-100 综合分，旗舰与高性价比兼顾。',
    key: 'overall',
    max: 100,
    label: '综合分',
    unit: '',
    fmt: identity,
  },
  {
    id: 'value',
    title: '性价比榜',
    desc: '按「能力分 ÷ Nexa 价」排序，便宜又好用的排前面。',
    key: 'valueScore',
    max: 100,
    label: '性价比指数',
    unit: '',
    fmt: identity,
  },
  {
    id: 'speed',
    title: '最快响应',
    desc: '按平均生成速度（tokens/s）排序，旗舰未必最快。',
    key: 'speed',
    max: 300,
    label: '生成速度',
    unit: ' tok/s',
    fmt: identity,
  },
  {
    id: 'popular',
    title: '最受欢迎',
    desc: '按本月经 Nexa 网关的真实调用量排序。',
    key: 'calls',
    max: 1_500_000,
    label: '本月调用',
    unit: '',
    fmt: fmtCalls,
  },
];

/** 按某榜的 key 降序排序。 */
export function sortForBoard(models: RankingModelVM[], key: BoardConfig['key']): RankingModelVM[] {
  return models.slice().sort((a, b) => b[key] - a[key]);
}
