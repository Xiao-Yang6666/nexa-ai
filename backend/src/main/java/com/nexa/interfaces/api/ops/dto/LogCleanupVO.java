package com.nexa.interfaces.api.ops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.application.ops.port.LogFileManager;

import java.util.List;

/**
 * 日志清理结果视图（接口层出参 DTO，F-4023 DELETE /api/performance/logs）。
 *
 * <p>对齐 API-ENDPOINTS §9.3 出参 {@code { deleted_count, freed_bytes, failed_files[] }}。
 * 部分失败时由 controller 据 {@link LogFileManager.CleanupResult#fullySucceeded()} 设 success=false。</p>
 *
 * @param deletedCount 删除文件数
 * @param freedBytes   释放字节数
 * @param failedFiles  删除失败的文件名
 */
public record LogCleanupVO(
        @JsonProperty("deleted_count") long deletedCount,
        @JsonProperty("freed_bytes") long freedBytes,
        @JsonProperty("failed_files") List<String> failedFiles) {

    /**
     * 由端口结果裁剪。
     *
     * @param result 清理结果
     * @return 视图
     */
    public static LogCleanupVO from(LogFileManager.CleanupResult result) {
        return new LogCleanupVO(
                result.deletedCount(),
                result.freedBytes(),
                result.failedFiles() == null ? List.of() : result.failedFiles());
    }
}
