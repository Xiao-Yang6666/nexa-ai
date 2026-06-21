package com.nexa.ops.application.port;

/**
 * 系统运行时探针（应用层端口，F-4019 性能统计 / F-4021 GC）。
 *
 * <p>抽象 JVM 运行时与系统资源的只读探测能力（内存/GC/线程/磁盘），由基础设施层用
 * {@code Runtime}/{@code MemoryMXBean}/{@code GarbageCollectorMXBean}/{@code File} 实现。
 * 应用层因此不直接依赖 {@code java.lang.management}，可用测试替身验证用例编排。</p>
 *
 * <p>对齐 API-ENDPOINTS §9.3 GET /api/performance/stats 的 memory_stats/disk_space_info 字段。</p>
 */
public interface SystemRuntimeProbe {

    /**
     * 采集当前运行时与磁盘快照（实时读取，不缓存）。
     *
     * @return 运行时统计快照
     */
    RuntimeStats sampleRuntimeStats();

    /**
     * 强制执行一次 GC（F-4021 POST /api/performance/gc 运维动作，幂等）。
     */
    void forceGarbageCollection();

    /**
     * 运行时与磁盘统计快照（只读值对象）。
     *
     * @param heapAllocBytes 已分配堆内存（字节，对齐 memory_stats.alloc）
     * @param totalMemBytes  JVM 申请的系统内存（字节，对齐 memory_stats.sys）
     * @param gcCount        累计 GC 次数（对齐 memory_stats.num_gc）
     * @param threadCount    活动线程数（对齐 memory_stats.num_goroutine 的 JVM 类比）
     * @param diskTotalBytes 数据目录所在磁盘总空间（字节）
     * @param diskUsableBytes 数据目录所在磁盘可用空间（字节）
     */
    record RuntimeStats(long heapAllocBytes,
                        long totalMemBytes,
                        long gcCount,
                        int threadCount,
                        long diskTotalBytes,
                        long diskUsableBytes) {

        /**
         * 磁盘已用百分比（对齐 disk_space_info.used_percent；总空间为 0 时返回 0 防除零）。
         *
         * @return 0~100 的百分比
         */
        public double diskUsedPercent() {
            if (diskTotalBytes <= 0) {
                return 0.0;
            }
            long used = diskTotalBytes - diskUsableBytes;
            return (used * 100.0) / diskTotalBytes;
        }
    }
}
