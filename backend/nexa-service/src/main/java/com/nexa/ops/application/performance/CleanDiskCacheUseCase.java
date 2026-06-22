package com.nexa.ops.application.performance;

import com.nexa.ops.application.port.DiskCacheManager;
import org.springframework.stereotype.Service;

/**
 * 清理不活跃磁盘缓存用例（应用层，F-4020 DELETE /api/performance/disk_cache）。
 *
 * <p>编排：委托磁盘缓存端口按 10 分钟不活跃阈值清理（保护进行中请求）。趋于幂等运维动作。</p>
 */
@Service
public class CleanDiskCacheUseCase {

    private final DiskCacheManager diskCacheManager;

    /**
     * @param diskCacheManager 磁盘缓存端口
     */
    public CleanDiskCacheUseCase(DiskCacheManager diskCacheManager) {
        this.diskCacheManager = diskCacheManager;
    }

    /**
     * 清理不活跃磁盘缓存。
     *
     * @return 清理结果（删除数 + 释放字节）
     */
    public DiskCacheManager.CleanupResult execute() {
        return diskCacheManager.cleanupInactive();
    }
}
