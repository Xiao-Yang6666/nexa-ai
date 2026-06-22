package com.nexa.ops.application.port;

/**
 * 磁盘缓存管理端口（应用层端口，F-4019 缓存信息 / F-4020 清理不活跃磁盘缓存）。
 *
 * <p>抽象磁盘缓存目录的统计与清理能力，由基础设施层用文件系统实现。清理须保护进行中请求
 * （按 10 分钟不活跃阈值，API-ENDPOINTS §9.3 F-4020）。应用层不直接操作文件系统。</p>
 */
public interface DiskCacheManager {

    /** 不活跃清理阈值（秒）：早于此的缓存文件才删，保护进行中请求（F-4020）。 */
    long INACTIVE_THRESHOLD_SECONDS = 600;

    /**
     * 当前磁盘缓存概况（F-4019 disk_cache_info）。
     *
     * @return 缓存概况
     */
    DiskCacheInfo info();

    /**
     * 清理不活跃磁盘缓存文件（F-4020，按 {@link #INACTIVE_THRESHOLD_SECONDS} 阈值）。
     *
     * @return 本次清理结果
     */
    CleanupResult cleanupInactive();

    /**
     * 磁盘缓存概况（只读）。
     *
     * @param enabled    是否启用磁盘缓存（未配置目录→false）
     * @param fileCount  当前缓存文件数
     * @param totalBytes 当前缓存占用字节
     */
    record DiskCacheInfo(boolean enabled, long fileCount, long totalBytes) {
    }

    /**
     * 清理结果（只读）。
     *
     * @param deletedCount 删除文件数
     * @param freedBytes   释放字节数
     */
    record CleanupResult(long deletedCount, long freedBytes) {
    }
}
