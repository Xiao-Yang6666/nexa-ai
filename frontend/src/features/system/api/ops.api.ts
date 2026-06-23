/**
 * features/system/api/ops — 系统设置页运维操作端点（/api/ops/*，AdminAuth）。
 * 路径/方法/出参对齐后端 PerformanceController（R4-B）：
 *
 *   - GET  /api/ops/performance   性能统计（含 disk_cache_info.total_bytes 真实缓存占用）
 *   - POST /api/ops/cache/clear   清空不活跃磁盘缓存 → { deleted_count, freed_bytes }
 *   - POST /api/ops/stats/reset   重置缓存命中统计计数（幂等）
 */
import { http } from '@/shared/api';

/** GET /api/ops/performance 出参（仅取页面需要的字段）。 */
export interface PerformanceStats {
  disk_cache_info?: {
    enabled?: boolean;
    file_count?: number;
    total_bytes?: number;
  };
}

/** POST /api/ops/cache/clear 出参。 */
export interface CacheCleanupResult {
  deleted_count?: number;
  freed_bytes?: number;
}

/** 性能统计查询（缓存占用真实值来源）。 */
export function getPerformanceStats(): Promise<PerformanceStats> {
  return http.get<PerformanceStats>('/api/ops/performance');
}

/** 清空不活跃磁盘缓存。 */
export function clearDiskCache(): Promise<CacheCleanupResult> {
  return http.post<CacheCleanupResult>('/api/ops/cache/clear');
}

/** 重置缓存命中统计计数。 */
export function resetStats(): Promise<void> {
  return http.post<void>('/api/ops/stats/reset');
}
