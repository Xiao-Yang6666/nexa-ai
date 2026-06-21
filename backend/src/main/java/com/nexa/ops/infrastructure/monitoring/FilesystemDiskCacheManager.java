package com.nexa.ops.infrastructure.monitoring;

import com.nexa.ops.application.port.DiskCacheManager;
import com.nexa.ops.infrastructure.config.OpsProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Instant;

/**
 * {@link DiskCacheManager} 的文件系统实现（基础设施层，F-4019 缓存信息 / F-4020 清理）。
 *
 * <p>对配置的磁盘缓存目录做统计与清理。清理只删早于 {@link DiskCacheManager#INACTIVE_THRESHOLD_SECONDS}
 * （10 分钟）的文件，保护进行中请求正在使用的缓存（F-4020）。目录未配置→{@code enabled=false}，
 * 清理无副作用（趋于幂等）。应用层经端口依赖本实现，不直接操作文件系统（DDD §2.3）。</p>
 */
@Component
public class FilesystemDiskCacheManager implements DiskCacheManager {

    private final OpsProperties opsProperties;

    /**
     * @param opsProperties 运维配置（缓存目录）
     */
    public FilesystemDiskCacheManager(OpsProperties opsProperties) {
        this.opsProperties = opsProperties;
    }

    /** {@inheritDoc} */
    @Override
    public DiskCacheInfo info() {
        File dir = cacheDir();
        if (dir == null) {
            return new DiskCacheInfo(false, 0, 0);
        }
        long count = 0;
        long total = 0;
        File[] files = dir.listFiles(File::isFile);
        if (files != null) {
            for (File f : files) {
                count++;
                total += f.length();
            }
        }
        return new DiskCacheInfo(true, count, total);
    }

    /** {@inheritDoc} */
    @Override
    public CleanupResult cleanupInactive() {
        File dir = cacheDir();
        if (dir == null) {
            return new CleanupResult(0, 0);
        }
        // 早于该时刻（epoch 秒）最后修改的文件才删，保护 10 分钟内活跃缓存。
        long cutoffSeconds = Instant.now().getEpochSecond() - INACTIVE_THRESHOLD_SECONDS;
        long deleted = 0;
        long freed = 0;
        File[] files = dir.listFiles(File::isFile);
        if (files != null) {
            for (File f : files) {
                long lastModifiedSeconds = f.lastModified() / 1000L;
                if (lastModifiedSeconds < cutoffSeconds) {
                    long size = f.length();
                    if (f.delete()) {
                        deleted++;
                        freed += size;
                    }
                    // 删除失败（被占用/权限）静默跳过：清理为尽力而为运维动作，单文件失败不阻塞整体。
                }
            }
        }
        return new CleanupResult(deleted, freed);
    }

    /**
     * 解析缓存目录（未配置或不存在/非目录返回 null → enabled=false）。
     *
     * @return 缓存目录 File，或 null
     */
    private File cacheDir() {
        String path = opsProperties.getDiskCache().getDir();
        if (path == null || path.isBlank()) {
            return null;
        }
        File dir = new File(path);
        return (dir.exists() && dir.isDirectory()) ? dir : null;
    }
}
