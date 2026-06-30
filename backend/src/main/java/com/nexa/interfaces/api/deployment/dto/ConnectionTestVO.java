package com.nexa.interfaces.api.deployment.dto;

import com.nexa.domain.deployment.model.ConnectionTestResult;

/**
 * io.net 连接测试出参视图（管理端视图，F-3040）。
 *
 * <p>对齐契约出参 {@code { hardware_count, total_available }}（API-ENDPOINTS §10.1）。
 * 取自领域 {@link ConnectionTestResult}（由硬件目录投影派生）。不返回任何凭证。</p>
 *
 * @param hardwareCount  硬件类型数
 * @param totalAvailable 总可用副本数
 */
public record ConnectionTestVO(int hardwareCount, long totalAvailable) {

    /**
     * 从领域结果投影为出参视图。
     *
     * @param result 连接测试结果
     * @return 出参视图
     */
    public static ConnectionTestVO from(ConnectionTestResult result) {
        return new ConnectionTestVO(result.hardwareCount(), result.totalAvailable());
    }
}
