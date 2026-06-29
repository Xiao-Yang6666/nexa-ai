package com.nexa.infrastructure.ops.monitoring;

import com.nexa.application.ops.port.LogFileManager;
import com.nexa.domain.ops.exception.InvalidMaintenanceRequestException;
import com.nexa.infrastructure.ops.config.OpsProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@link LogFileManager} 的文件系统实现（基础设施层，F-4022 列表 / F-4023 清理）。
 *
 * <p>仅识别前缀 {@code oneapi-} + 后缀 {@code .log} 的日志文件（保护目录内其他文件不被误删）。
 * {@code LogDir} 未配置→{@link #isEnabled()} false（F-4022 enabled:false）。清理支持 by_count
 * （保留最新 N 个）/ by_days（删早于 N 天），跳过当前活动日志（F-4023）。应用层经端口依赖本实现。</p>
 */
@Component
public class FilesystemLogFileManager implements LogFileManager {

    /** 清理模式：保留最新 N 个。 */
    private static final String MODE_BY_COUNT = "by_count";
    /** 清理模式：删除早于 N 天。 */
    private static final String MODE_BY_DAYS = "by_days";
    private static final long SECONDS_PER_DAY = 86_400L;

    private final OpsProperties opsProperties;

    /**
     * @param opsProperties 运维配置（日志目录 + 当前活动日志名）
     */
    public FilesystemLogFileManager(OpsProperties opsProperties) {
        this.opsProperties = opsProperties;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEnabled() {
        String dir = opsProperties.getLogs().getDir();
        return dir != null && !dir.isBlank();
    }

    /** {@inheritDoc} */
    @Override
    public List<LogFileInfo> listLogFiles() {
        File dir = logDir();
        if (dir == null) {
            return List.of();
        }
        List<LogFileInfo> result = new ArrayList<>();
        File[] files = dir.listFiles(this::isManagedLog);
        if (files != null) {
            for (File f : files) {
                result.add(new LogFileInfo(f.getName(), f.length(), f.lastModified() / 1000L));
            }
        }
        // 按文件名降序（与契约「按名降序」一致；日志名通常含日期，降序即新在前）。
        result.sort(Comparator.comparing(LogFileInfo::name).reversed());
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public CleanupResult cleanup(String mode, int value) {
        // 前置校验（F-4023 错误码）：mode 白名单、value≥1、LogDir 已配置。
        if (!MODE_BY_COUNT.equals(mode) && !MODE_BY_DAYS.equals(mode)) {
            throw new InvalidMaintenanceRequestException("invalid mode");
        }
        if (value < 1) {
            throw new InvalidMaintenanceRequestException("invalid value");
        }
        File dir = logDir();
        if (dir == null) {
            throw new InvalidMaintenanceRequestException("日志目录未配置，无法清理");
        }

        List<LogFileInfo> logs = listLogFiles(); // 已按名降序（新在前）
        String currentName = opsProperties.getLogs().getCurrentFileName();

        long deleted = 0;
        long freed = 0;
        List<String> failed = new ArrayList<>();

        if (MODE_BY_COUNT.equals(mode)) {
            // 保留最新 value 个，删其余（跳过当前活动日志）。
            for (int i = value; i < logs.size(); i++) {
                LogFileInfo info = logs.get(i);
                if (isCurrent(info.name(), currentName)) {
                    continue;
                }
                DeleteOutcome outcome = deleteOne(dir, info);
                deleted += outcome.deleted();
                freed += outcome.freed();
                if (outcome.failed()) {
                    failed.add(info.name());
                }
            }
        } else {
            // by_days：删最后修改早于 (now - value 天) 的日志（跳过当前活动日志）。
            long cutoffSeconds = Instant.now().getEpochSecond() - value * SECONDS_PER_DAY;
            for (LogFileInfo info : logs) {
                if (isCurrent(info.name(), currentName)) {
                    continue;
                }
                if (info.modifiedAt() < cutoffSeconds) {
                    DeleteOutcome outcome = deleteOne(dir, info);
                    deleted += outcome.deleted();
                    freed += outcome.freed();
                    if (outcome.failed()) {
                        failed.add(info.name());
                    }
                }
            }
        }
        return new CleanupResult(deleted, freed, failed);
    }

    /** 删除单文件，返回结果（删除成功 deleted=1+freed；失败 failed=true）。 */
    private DeleteOutcome deleteOne(File dir, LogFileInfo info) {
        File f = new File(dir, info.name());
        long size = f.length();
        if (f.delete()) {
            return new DeleteOutcome(1, size, false);
        }
        // 删除失败（占用/权限）记入 failed_files，对齐 F-4023 部分失败语义（不吞错，向上汇报）。
        return new DeleteOutcome(0, 0, true);
    }

    /** 单次删除结果（内部值对象）。 */
    private record DeleteOutcome(long deleted, long freed, boolean failed) {
    }

    private boolean isCurrent(String name, String currentName) {
        return currentName != null && !currentName.isBlank() && currentName.equals(name);
    }

    private boolean isManagedLog(File f) {
        return f.isFile() && f.getName().startsWith(LOG_PREFIX) && f.getName().endsWith(LOG_SUFFIX);
    }

    /**
     * 解析日志目录（未配置或不存在/非目录返回 null）。
     *
     * @return 日志目录 File，或 null
     */
    private File logDir() {
        String path = opsProperties.getLogs().getDir();
        if (path == null || path.isBlank()) {
            return null;
        }
        File dir = new File(path);
        return (dir.exists() && dir.isDirectory()) ? dir : null;
    }
}
