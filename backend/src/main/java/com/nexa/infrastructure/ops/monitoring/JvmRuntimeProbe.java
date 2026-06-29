package com.nexa.infrastructure.ops.monitoring;

import com.nexa.application.ops.port.SystemRuntimeProbe;
import com.nexa.infrastructure.ops.config.OpsProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * {@link SystemRuntimeProbe} 的 JVM 实现（基础设施层，F-4019 性能统计 / F-4021 GC）。
 *
 * <p>用 {@code java.lang.management} 与 {@code Runtime} 实时采集 JVM 内存/GC/线程与磁盘空间快照
 * （对齐现网 Go 版 {@code runtime.ReadMemStats} 的 JVM 类比）。应用层经端口依赖本实现，不直接
 * 触碰 management API（DDD §2.3）。磁盘空间取磁盘缓存目录所在卷，未配置时取工作目录卷。</p>
 */
@Component
public class JvmRuntimeProbe implements SystemRuntimeProbe {

    private final OpsProperties opsProperties;

    /**
     * @param opsProperties 运维配置（用于定位磁盘空间统计的目录）
     */
    public JvmRuntimeProbe(OpsProperties opsProperties) {
        this.opsProperties = opsProperties;
    }

    /** {@inheritDoc} */
    @Override
    public RuntimeStats sampleRuntimeStats() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapAlloc = memoryBean.getHeapMemoryUsage().getUsed();

        Runtime runtime = Runtime.getRuntime();
        long totalMem = runtime.totalMemory();

        // 累计 GC 次数：跨所有 GC 收集器求和（不同 GC 算法暴露多个 bean）。
        long gcCount = 0;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = gcBean.getCollectionCount();
            if (c > 0) {
                gcCount += c;
            }
        }

        int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();

        // 磁盘空间：取缓存目录所在卷；未配置缓存目录则取当前工作目录所在卷。
        String dir = opsProperties.getDiskCache().getDir();
        File volume = (dir != null && !dir.isBlank()) ? new File(dir) : new File(".");
        long diskTotal = volume.getTotalSpace();
        long diskUsable = volume.getUsableSpace();

        return new RuntimeStats(heapAlloc, totalMem, gcCount, threadCount, diskTotal, diskUsable);
    }

    /** {@inheritDoc} */
    @Override
    public void forceGarbageCollection() {
        // 显式建议 JVM 执行 GC（运维动作，幂等）。System.gc 仅为建议，但与现网 runtime.GC() 语义对齐。
        System.gc();
    }
}
