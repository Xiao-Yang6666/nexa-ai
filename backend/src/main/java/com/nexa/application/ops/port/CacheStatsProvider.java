package com.nexa.application.ops.port;

/**
 * 应用层缓存统计端口（应用层端口，F-4019 cache_stats / F-4021 reset_stats）。
 *
 * <p>抽象进程内缓存（如渠道/模型/分组缓存）的命中统计与计数重置能力，由基础设施层实现。
 * F-4019 性能统计的 {@code cache_stats}、F-4021 POST /api/performance/reset_stats 经此承载。</p>
 */
public interface CacheStatsProvider {

    /**
     * 当前缓存统计快照（F-4019 cache_stats）。
     *
     * @return 缓存统计
     */
    CacheStats sample();

    /**
     * 重置性能统计计数（F-4021，幂等运维动作）。
     */
    void resetStats();

    /**
     * 缓存统计（只读）。
     *
     * @param hitCount  命中次数
     * @param missCount 未命中次数
     * @param entryCount 当前条目数
     */
    record CacheStats(long hitCount, long missCount, long entryCount) {

        /**
         * 命中率（0~1；总访问为 0 时返回 0 防除零）。
         *
         * @return 命中率
         */
        public double hitRate() {
            long total = hitCount + missCount;
            return total == 0 ? 0.0 : (double) hitCount / total;
        }
    }
}
