/**
 * features/system/api — 全站系统选项读写（/api/option/，RootAuth）。
 * 路径/方法/出参逐字对齐 openapi.yaml（模块九：ops F-4017/F-4018），不臆造字段。
 *
 *   - GET /api/option/        全站选项列表 {key,value}[]（已剔除敏感键）
 *   - PUT /api/option/        单键覆盖式更新（逐键校验，仅记 key 不记 value）
 */
import { http } from '@/shared/api';

/** /api/option/ 单项。 */
export interface OptionItem {
  key?: string;
  value?: string;
}

/**
 * 全站选项列表。
 * openapi: GET /api/option/ (F-4017, rootAuth) → ApiResponse{ data: {key,value}[] }
 */
export function getOptions(): Promise<OptionItem[]> {
  return http.get<OptionItem[]>('/api/option/');
}

/**
 * 更新单个选项键值。
 * openapi: PUT /api/option/ (F-4018, rootAuth) → ApiResponse
 */
export function updateOption(key: string, value: string): Promise<void> {
  return http.put<void>('/api/option/', { json: { key, value } });
}
