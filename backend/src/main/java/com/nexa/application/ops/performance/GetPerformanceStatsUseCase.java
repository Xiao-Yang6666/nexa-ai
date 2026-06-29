package com.nexa.application.ops.performance;

import com.nexa.application.ops.port.CacheStatsProvider;
import com.nexa.application.ops.port.DiskCacheManager;
import com.nexa.application.ops.port.SystemRuntimeProbe;
import org.springframework.stereotype.Service;

/**
 * 性能统计查询用例（应用层，F-4019 GET /api/performance/stats）。
 *
 * <p>编排：并列采样运行时、缓存、磁盘缓存三个端口的实时快照，聚合为 {@link PerformanceStats}。
 * 实时读取不缓存（运维需看当下真实值）。薄编排，无业务规则（backend-engineer §2.1）。</p>
 */
@Service
public class GetPerformanceStatsUseCase {

    private final SystemRuntimeProbe systemRuntimeProbe;
    private final CacheStatsProvider cacheStatsProvider;
    private final DiskCacheManager diskCacheManager;

    /**
     * @param systemRuntimeProbe 运行时探针
     * @param cacheStatsProvider 缓存统计端口
     * @param diskCacheManager   磁盘缓存端口
     */
    public GetPerformanceStatsUseCase(SystemRuntimeProbe systemRuntimeProbe,
                                      CacheStatsProvider cacheStatsProvider,
                                      DiskCacheManager diskCacheManager) {
        this.systemRuntimeProbe = systemRuntimeProbe;
        this.cacheStatsProvider = cacheStatsProvider;
        this.diskCacheManager = diskCacheManager;
    }

    /**
     * 采集当前性能统计快照。
     *
     * @return 聚合性能统计
     */
    public PerformanceStats execute() {
        return new PerformanceStats(
                systemRuntimeProbe.sampleRuntimeStats(),
                cacheStatsProvider.sample(),
                diskCacheManager.info());
    }
}
