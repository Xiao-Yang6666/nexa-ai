/**
 * features/billing/model/ratio.model — 计费倍率视图模型 + React Query hook。
 *
 * 把 /api/option/ 返回的扁平 {key,value}[] 中的倍率 JSON 字符串解析为统一的倍率行列表：
 *   ModelRatio / CompletionRatio / CacheRatio → 按模型名归并为 model 行（输入/输出/缓存倍率）
 *   GroupRatio → group 行（分组折扣，仅输入列有意义）
 * 解析失败（非法 JSON）按空对象处理，不抛错，保证页面可降级展示。
 */
import { useQuery } from '@tanstack/react-query';
import { getOptions, type OptionItem } from '../api/ratio.api';

/** 倍率行类型。 */
export type RatioKind = 'model' | 'group';

/** 倍率配置行视图模型。 */
export interface RatioRowVM {
  /** 模型名或分组名 */
  nm: string;
  kind: RatioKind;
  /** 输入倍率（model）/ 分组折扣（group） */
  in: number;
  /** 输出/补全倍率（group 行为同值或 0） */
  out: number;
  /** 缓存倍率（无则 0） */
  cache: number;
}

/** 安全解析 JSON 对象（数值 map）；失败返回空对象。 */
function parseRatioMap(value: string | undefined): Record<string, number> {
  if (!value) return {};
  try {
    const obj = JSON.parse(value);
    if (obj && typeof obj === 'object') {
      const out: Record<string, number> = {};
      for (const [k, v] of Object.entries(obj)) {
        const n = typeof v === 'number' ? v : parseFloat(String(v));
        if (!Number.isNaN(n)) out[k] = n;
      }
      return out;
    }
  } catch {
    /* 非法 JSON：降级为空 */
  }
  return {};
}

function findOption(items: OptionItem[], key: string): string | undefined {
  return items.find((o) => o.key === key)?.value;
}

/** 由选项列表组装倍率行。 */
export function toRatioRows(items: OptionItem[]): RatioRowVM[] {
  const modelRatio = parseRatioMap(findOption(items, 'ModelRatio'));
  const completionRatio = parseRatioMap(findOption(items, 'CompletionRatio'));
  const cacheRatio = parseRatioMap(findOption(items, 'CacheRatio'));
  const groupRatio = parseRatioMap(findOption(items, 'GroupRatio'));

  const rows: RatioRowVM[] = [];

  // 模型倍率：以 ModelRatio 的键为主集，并入补全/缓存倍率
  const modelNames = new Set<string>([
    ...Object.keys(modelRatio),
    ...Object.keys(completionRatio),
    ...Object.keys(cacheRatio),
  ]);
  for (const nm of modelNames) {
    rows.push({
      nm,
      kind: 'model',
      in: modelRatio[nm] ?? 0,
      out: (modelRatio[nm] ?? 1) * (completionRatio[nm] ?? 1),
      cache: cacheRatio[nm] ?? 0,
    });
  }

  // 分组倍率
  for (const [nm, ratio] of Object.entries(groupRatio)) {
    rows.push({ nm: `${nm} 分组`, kind: 'group', in: ratio, out: ratio, cache: ratio });
  }

  return rows;
}

/** 倍率档位分布（输入倍率分箱），用于分布图。 */
export interface RatioBin {
  lab: string;
  val: number;
}
export function toRatioBins(rows: RatioRowVM[]): RatioBin[] {
  const bins: { lab: string; lo: number; hi: number }[] = [
    { lab: '0–0.5', lo: 0, hi: 0.5 },
    { lab: '0.5–1', lo: 0.5, hi: 1 },
    { lab: '1–2', lo: 1, hi: 2 },
    { lab: '2–5', lo: 2, hi: 5 },
    { lab: '5–10', lo: 5, hi: 10 },
    { lab: '10+', lo: 10, hi: Infinity },
  ];
  return bins.map((b) => ({
    lab: b.lab,
    val: rows.filter((r) => r.kind === 'model' && r.in >= b.lo && r.in < b.hi).length,
  }));
}

/* ── React Query hook ──────────────────────────────────────────────────── */

/** 全站计费倍率查询 hook。返回 { rows, bins }。 */
export function useBillingRatios() {
  return useQuery({
    queryKey: ['billing', 'ratios'],
    queryFn: () => getOptions(),
    select: (items) => {
      const rows = toRatioRows(items);
      return { rows, bins: toRatioBins(rows) };
    },
  });
}
