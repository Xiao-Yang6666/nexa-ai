package com.nexa.ops.application.performance;

import com.nexa.ops.application.port.LogFileManager;
import com.nexa.ops.domain.exception.InvalidMaintenanceRequestException;
import org.springframework.stereotype.Service;

/**
 * 清理过期日志用例（应用层，F-4023 DELETE /api/performance/logs）。
 *
 * <p>编排：委托日志端口按策略清理（by_count/by_days）。入参校验（mode 白名单、value≥1、
 * LogDir 已配置）在端口实现内完成并抛 {@link InvalidMaintenanceRequestException}（→400），
 * 跳过当前活动日志（F-4023）。趋于幂等。</p>
 */
@Service
public class CleanLogsUseCase {

    private final LogFileManager logFileManager;

    /**
     * @param logFileManager 日志文件端口
     */
    public CleanLogsUseCase(LogFileManager logFileManager) {
        this.logFileManager = logFileManager;
    }

    /**
     * 清理过期日志。
     *
     * @param mode  清理模式（by_count/by_days）
     * @param value 阈值（≥1）
     * @return 清理结果（删除数 + 释放字节 + 失败文件）
     * @throws InvalidMaintenanceRequestException mode 非法 / value&lt;1 / LogDir 未配置（→400）
     */
    public LogFileManager.CleanupResult execute(String mode, int value) {
        return logFileManager.cleanup(mode, value);
    }
}
