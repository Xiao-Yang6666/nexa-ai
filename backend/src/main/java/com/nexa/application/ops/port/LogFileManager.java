package com.nexa.application.ops.port;

import com.nexa.domain.ops.exception.InvalidMaintenanceRequestException;

import java.util.List;

/**
 * 日志文件管理端口（应用层端口，F-4022 列表 / F-4023 清理）。
 *
 * <p>抽象日志目录（仅前缀 {@code oneapi-} + 后缀 {@code .log} 文件）的列举与按策略清理能力，
 * 由基础设施层用文件系统实现。{@code LogDir} 未配置时 {@link #isEnabled()} 返回 false
 * （F-4022 出参 {@code enabled:false}）。清理须跳过当前活动日志（F-4023）。</p>
 */
public interface LogFileManager {

    /** 日志文件名前缀（仅清理/列举该前缀日志，保护其他文件）。 */
    String LOG_PREFIX = "oneapi-";
    /** 日志文件名后缀。 */
    String LOG_SUFFIX = ".log";

    /**
     * 日志目录是否已配置（F-4022：未配置→enabled:false）。
     *
     * @return 已配置返回 {@code true}
     */
    boolean isEnabled();

    /**
     * 列举日志文件（F-4022，按名降序）。
     *
     * @return 日志文件列表（未启用时为空列表，调用方据 isEnabled 决定出参形态）
     */
    List<LogFileInfo> listLogFiles();

    /**
     * 按策略清理过期日志（F-4023）。
     *
     * @param mode  清理模式（{@code by_count} 保留最新 N 个 / {@code by_days} 删早于 N 天）
     * @param value 阈值（≥1，by_count=保留数，by_days=天数）
     * @return 清理结果
     * @throws InvalidMaintenanceRequestException mode 非法 / value&lt;1 / LogDir 未配置（→400）
     */
    CleanupResult cleanup(String mode, int value);

    /**
     * 日志文件信息（只读）。
     *
     * @param name      文件名
     * @param sizeBytes 大小（字节）
     * @param modifiedAt 最后修改时间 epoch 秒
     */
    record LogFileInfo(String name, long sizeBytes, long modifiedAt) {
    }

    /**
     * 日志清理结果（F-4023 出参）。
     *
     * @param deletedCount 删除文件数
     * @param freedBytes   释放字节数
     * @param failedFiles  删除失败的文件名（部分失败时 success=false）
     */
    record CleanupResult(long deletedCount, long freedBytes, List<String> failedFiles) {

        /** @return 是否全部成功（无失败文件） */
        public boolean fullySucceeded() {
            return failedFiles == null || failedFiles.isEmpty();
        }
    }
}
