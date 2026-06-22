package com.nexa.ops.application.performance;

import com.nexa.ops.application.port.CacheStatsProvider;
import org.springframework.stereotype.Service;

/**
 * 重置性能统计计数用例（应用层，F-4021 POST /api/performance/reset_stats）。
 *
 * <p>编排：委托缓存统计端口重置命中/未命中计数。幂等运维动作。</p>
 */
@Service
public class ResetStatsUseCase {

    private final CacheStatsProvider cacheStatsProvider;

    /**
     * @param cacheStatsProvider 缓存统计端口
     */
    public ResetStatsUseCase(CacheStatsProvider cacheStatsProvider) {
        this.cacheStatsProvider = cacheStatsProvider;
    }

    /** 重置性能统计计数。 */
    public void execute() {
        cacheStatsProvider.resetStats();
    }
}
