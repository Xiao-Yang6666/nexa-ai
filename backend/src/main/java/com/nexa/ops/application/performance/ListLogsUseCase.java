package com.nexa.ops.application.performance;

import com.nexa.ops.application.port.LogFileManager;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 日志文件列表查询用例（应用层，F-4022 GET /api/performance/logs）。
 *
 * <p>编排：委托日志端口判断是否启用 + 列举。接口层据 {@link #isEnabled()} 决定出参形态
 * （未启用→{@code enabled:false}；启用→文件列表 + 汇总，API-ENDPOINTS §9.3）。</p>
 */
@Service
public class ListLogsUseCase {

    private final LogFileManager logFileManager;

    /**
     * @param logFileManager 日志文件端口
     */
    public ListLogsUseCase(LogFileManager logFileManager) {
        this.logFileManager = logFileManager;
    }

    /**
     * 日志目录是否已配置。
     *
     * @return 已配置返回 {@code true}
     */
    public boolean isEnabled() {
        return logFileManager.isEnabled();
    }

    /**
     * 列举日志文件（按名降序）。
     *
     * @return 日志文件列表
     */
    public List<LogFileManager.LogFileInfo> execute() {
        return logFileManager.listLogFiles();
    }
}
