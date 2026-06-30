package com.nexa.interfaces.api.ops.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.application.ops.port.LogFileManager;

import java.util.List;

/**
 * 日志文件列表视图（接口层出参 DTO，F-4022 GET /api/performance/logs）。
 *
 * <p>对齐 API-ENDPOINTS §9.3：LogDir 未配置→{@code {enabled:false}}（其余字段省略）；否则
 * {@code {enabled:true, files[], file_count, total_size, oldest_time, newest_time}}。汇总（总数/
 * 总大小/最早最晚时间）在视图组装时从文件列表派生。</p>
 *
 * @param enabled    日志目录是否已配置
 * @param files      日志文件列表（未启用时省略）
 * @param fileCount  文件总数
 * @param totalSize  总字节
 * @param oldestTime 最早修改时间 epoch 秒
 * @param newestTime 最晚修改时间 epoch 秒
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogListVO(
        boolean enabled,
        List<LogFileView> files,
        @JsonProperty("file_count") Integer fileCount,
        @JsonProperty("total_size") Long totalSize,
        @JsonProperty("oldest_time") Long oldestTime,
        @JsonProperty("newest_time") Long newestTime) {

    /**
     * 未启用视图（LogDir 未配置）。
     *
     * @return 仅 enabled=false
     */
    public static LogListVO disabled() {
        return new LogListVO(false, null, null, null, null, null);
    }

    /**
     * 由文件列表组装启用视图（派生汇总）。
     *
     * @param infos 日志文件信息列表
     * @return 启用视图
     */
    public static LogListVO enabled(List<LogFileManager.LogFileInfo> infos) {
        List<LogFileView> files = infos.stream().map(LogFileView::from).toList();
        long total = infos.stream().mapToLong(LogFileManager.LogFileInfo::sizeBytes).sum();
        Long oldest = infos.stream().mapToLong(LogFileManager.LogFileInfo::modifiedAt).min().stream().boxed().findFirst().orElse(null);
        Long newest = infos.stream().mapToLong(LogFileManager.LogFileInfo::modifiedAt).max().stream().boxed().findFirst().orElse(null);
        return new LogListVO(true, files, files.size(), total, oldest, newest);
    }

    /**
     * 单个日志文件视图。
     *
     * @param name         文件名
     * @param size         大小字节
     * @param modifiedTime 最后修改时间 epoch 秒
     */
    public record LogFileView(
            String name,
            long size,
            @JsonProperty("modified_time") long modifiedTime) {

        /**
         * 由端口信息裁剪。
         *
         * @param info 日志文件信息
         * @return 视图
         */
        public static LogFileView from(LogFileManager.LogFileInfo info) {
            return new LogFileView(info.name(), info.sizeBytes(), info.modifiedAt());
        }
    }
}
