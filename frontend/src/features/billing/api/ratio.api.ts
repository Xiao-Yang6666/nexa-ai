/**
 * features/billing/api/ratio.api — 全站计费倍率读取（基于 /api/option/，RootAuth）。
 *
 * new-api 惯例：模型/分组/补全/缓存倍率以 JSON 字符串存于系统选项（Option）：
 *   - ModelRatio:        {"gpt-4o": 2.5, ...}（模型输入倍率）
 *   - ModelPrice:        {"gpt-4o": 0, ...}（按量固定价，可选）
 *   - CompletionRatio:   {"gpt-4o": 3, ...}（补全/输出倍率）
 *   - GroupRatio:        {"default": 1, "vip": 0.8, ...}（分组折扣）
 *   - CacheRatio:        {"gpt-4o": 0.25, ...}（缓存命中倍率，可选）
 * 这些键由 GET /api/option/ 一并返回（{key,value}[]，已剔除敏感键）。
 */
import { http } from '@/shared/api';

/** /api/option/ 单项。 */
export interface OptionItem {
  key?: string;
  value?: string;
}

/**
 * 全站选项列表（含各类倍率 JSON 字符串）。
 * openapi: GET /api/option/ (F-4017, rootAuth) → ApiResponse{ data: {key,value}[] }
 */
export function getOptions(): Promise<OptionItem[]> {
  return http.get<OptionItem[]>('/api/option/');
}
