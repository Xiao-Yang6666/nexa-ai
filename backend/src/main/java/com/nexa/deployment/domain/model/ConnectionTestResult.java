package com.nexa.deployment.domain.model;

/**
 * io.net 连接测试结果（领域模型，F-3040）。
 *
 * <p>承载「io.net 连接测试」结果：契约出参 {@code { hardware_count, total_available }}
 * （API-ENDPOINTS §10.1 F-3040）。连接测试通过「拉取硬件类型目录」校验 api_key 有效性，
 * 并从硬件目录派生总数与总可用量——即测试成功的副产物即为可用硬件概览（与上游 newapi 行为对齐：
 * 「连接测试 + 返回硬件可用量」）。</p>
 *
 * <p>本结果由 {@link HardwareCatalog} 投影而来（{@code hardwareCount=类型数}、
 * {@code totalAvailable=各类型可用副本之和}），复用同一派生口径，避免与 F-3049 硬件查询口径漂移。</p>
 *
 * @param hardwareCount  硬件类型数
 * @param totalAvailable 总可用副本数
 */
public record ConnectionTestResult(int hardwareCount, long totalAvailable) {

    /**
     * 从硬件目录聚合投影为连接测试结果。
     *
     * <p>领域规则来源：API-ENDPOINTS §10.1 F-3040。测试 api_key 有效即能成功拉取硬件目录，
     * 故以硬件目录的派生量作为连接测试的返回。</p>
     *
     * @param catalog 硬件类型聚合（连接测试时拉取的上游结果）
     * @return 连接测试结果
     */
    public static ConnectionTestResult from(HardwareCatalog catalog) {
        return new ConnectionTestResult(catalog.total(), catalog.totalAvailable());
    }
}
