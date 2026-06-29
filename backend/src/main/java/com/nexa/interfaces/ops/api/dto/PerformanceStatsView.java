package com.nexa.interfaces.ops.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.application.ops.performance.PerformanceStats;
import com.nexa.application.ops.port.CacheStatsProvider;
import com.nexa.application.ops.port.DiskCacheManager;
import com.nexa.application.ops.port.SystemRuntimeProbe;

/**
 * 性能统计视图（接口层出参 DTO，F-4019 GET /api/performance/stats）。
 *
 * <p>对齐 API-ENDPOINTS §9.3 出参结构 {@code { cache_stats, memory_stats, disk_cache_info,
 * disk_space_info, performance_config }}。把应用层 {@link PerformanceStats} 聚合裁剪为契约形态，
 * 字段名（snake_case + num_gc/num_goroutine 等 Go 风格残留）经 {@link JsonProperty} 对齐。</p>
 *
 * @param cacheStats        缓存命中统计
 * @param memoryStats       内存/GC/线程统计
 * @param diskCacheInfo     磁盘缓存概况
 * @param diskSpaceInfo     磁盘空间
 * @param performanceConfig 性能阈值配置（占位，待 perf 配置项落地）
 */
public record PerformanceStatsView(
        @JsonProperty("cache_stats") CacheStatsView cacheStats,
        @JsonProperty("memory_stats") MemoryStatsView memoryStats,
        @JsonProperty("disk_cache_info") DiskCacheInfoView diskCacheInfo,
        @JsonProperty("disk_space_info") DiskSpaceInfoView diskSpaceInfo,
        @JsonProperty("performance_config") PerformanceConfigView performanceConfig) {

    /**
     * 由应用层聚合结果裁剪为视图。
     *
     * @param stats 性能统计聚合
     * @return 视图
     */
    public static PerformanceStatsView from(PerformanceStats stats) {
        SystemRuntimeProbe.RuntimeStats rt = stats.runtime();
        CacheStatsProvider.CacheStats cache = stats.cache();
        DiskCacheManager.DiskCacheInfo dc = stats.diskCache();
        return new PerformanceStatsView(
                new CacheStatsView(cache.hitCount(), cache.missCount(), cache.entryCount(), cache.hitRate()),
                new MemoryStatsView(rt.heapAllocBytes(), rt.totalMemBytes(), rt.gcCount(), rt.threadCount()),
                new DiskCacheInfoView(dc.enabled(), dc.fileCount(), dc.totalBytes()),
                new DiskSpaceInfoView(rt.diskTotalBytes(), rt.diskUsableBytes(), rt.diskUsedPercent()),
                PerformanceConfigView.defaults());
    }

    /**
     * 缓存命中统计视图。
     *
     * @param hitCount  命中数
     * @param missCount 未命中数
     * @param entryCount 条目数
     * @param hitRate   命中率（0~1）
     */
    public record CacheStatsView(
            @JsonProperty("hit_count") long hitCount,
            @JsonProperty("miss_count") long missCount,
            @JsonProperty("entry_count") long entryCount,
            @JsonProperty("hit_rate") double hitRate) {
    }

    /**
     * 内存/GC/线程统计视图（字段名沿用契约的 Go 风格 alloc/sys/num_gc/num_goroutine）。
     *
     * @param alloc        已分配堆内存字节
     * @param sys          JVM 申请系统内存字节
     * @param numGc        累计 GC 次数
     * @param numGoroutine 活动线程数（JVM 类比 goroutine）
     */
    public record MemoryStatsView(
            @JsonProperty("alloc") long alloc,
            @JsonProperty("sys") long sys,
            @JsonProperty("num_gc") long numGc,
            @JsonProperty("num_goroutine") int numGoroutine) {
    }

    /**
     * 磁盘缓存概况视图。
     *
     * @param enabled    是否启用磁盘缓存
     * @param fileCount  缓存文件数
     * @param totalBytes 占用字节
     */
    public record DiskCacheInfoView(
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("file_count") long fileCount,
            @JsonProperty("total_bytes") long totalBytes) {
    }

    /**
     * 磁盘空间视图。
     *
     * @param totalBytes  总空间字节
     * @param usableBytes 可用空间字节
     * @param usedPercent 已用百分比（0~100）
     */
    public record DiskSpaceInfoView(
            @JsonProperty("total_bytes") long totalBytes,
            @JsonProperty("usable_bytes") long usableBytes,
            @JsonProperty("used_percent") double usedPercent) {
    }

    /**
     * 性能阈值配置视图（占位默认值，待 performance_config 选项项落地后接入）。
     *
     * @param cpuThresholdPercent  CPU 阈值
     * @param memoryThresholdPercent 内存阈值
     * @param diskThresholdPercent   磁盘阈值
     */
    public record PerformanceConfigView(
            @JsonProperty("cpu_threshold_percent") int cpuThresholdPercent,
            @JsonProperty("memory_threshold_percent") int memoryThresholdPercent,
            @JsonProperty("disk_threshold_percent") int diskThresholdPercent) {

        /** @return 默认阈值（80/80/90，待配置化后由 OpsProperties 提供） */
        public static PerformanceConfigView defaults() {
            return new PerformanceConfigView(80, 80, 90);
        }
    }
}
