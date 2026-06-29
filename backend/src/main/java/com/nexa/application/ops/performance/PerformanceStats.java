package com.nexa.application.ops.performance;

import com.nexa.application.ops.port.CacheStatsProvider;
import com.nexa.application.ops.port.DiskCacheManager;
import com.nexa.application.ops.port.SystemRuntimeProbe;

/**
 * 性能统计聚合结果（应用层只读载荷，F-4019 GET /api/performance/stats）。
 *
 * <p>聚合运行时（内存/GC/线程）、缓存命中、磁盘缓存概况、磁盘空间四类快照，供接口层裁剪为
 * {@code { cache_stats, memory_stats, disk_cache_info, disk_space_info, performance_config }}
 * 视图（API-ENDPOINTS §9.3）。纯数据载荷，不含行为。</p>
 *
 * @param runtime   运行时与磁盘空间快照
 * @param cache     应用缓存命中统计
 * @param diskCache 磁盘缓存概况
 */
public record PerformanceStats(SystemRuntimeProbe.RuntimeStats runtime,
                               CacheStatsProvider.CacheStats cache,
                               DiskCacheManager.DiskCacheInfo diskCache) {
}
