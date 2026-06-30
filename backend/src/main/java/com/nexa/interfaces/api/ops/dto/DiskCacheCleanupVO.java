package com.nexa.interfaces.api.ops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.application.ops.port.DiskCacheManager;

/**
 * 磁盘缓存清理结果视图（接口层出参 DTO，POST /api/ops/cache/clear）。
 *
 * <p>把应用层 {@link DiskCacheManager.CleanupResult} 裁剪为契约形态，字段名 snake_case。</p>
 *
 * @param deletedCount 删除的缓存文件数
 * @param freedBytes   释放的字节数
 */
public record DiskCacheCleanupVO(
        @JsonProperty("deleted_count") long deletedCount,
        @JsonProperty("freed_bytes") long freedBytes) {

    /**
     * 由应用层清理结果裁剪为视图。
     *
     * @param result 清理结果
     * @return 视图
     */
    public static DiskCacheCleanupVO from(DiskCacheManager.CleanupResult result) {
        return new DiskCacheCleanupVO(result.deletedCount(), result.freedBytes());
    }
}
