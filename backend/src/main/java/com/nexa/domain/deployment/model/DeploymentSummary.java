package com.nexa.domain.deployment.model;

/**
 * 部署概要（领域模型，F-3041/F-3042 列表行）。
 *
 * <p>承载「部署列表/搜索」单行数据：契约 {@code items[]} =
 * {@code { deployment_id, name, status, time_remaining, hardware_info, compute_minutes_remaining }}
 * （API-ENDPOINTS §10.2 F-3041）。映射自上游部署列表项（infra 层完成字段抽取 + 脱敏）。</p>
 *
 * <p>{@code status} 是状态计数（{@link DeploymentList#statusCounts()}）与状态过滤（F-3042）的依据；
 * {@code name} 是名称关键词本地过滤（F-3042）的依据。</p>
 *
 * @param deploymentId          部署 ID
 * @param name                  部署名称（关键词过滤依据）
 * @param status                部署状态（状态计数/过滤依据，如 running/completed/failed）
 * @param timeRemaining         剩余时间（上游原值，可空）
 * @param hardwareInfo          硬件信息摘要（上游原值，可空）
 * @param computeMinutesRemaining 剩余计算分钟（上游原值，可空）
 */
public record DeploymentSummary(
        String deploymentId,
        String name,
        String status,
        String timeRemaining,
        String hardwareInfo,
        Long computeMinutesRemaining) {

    /**
     * 名称是否包含给定关键词（小写包含匹配，F-3042 本地过滤规则）。
     *
     * <p>领域规则来源：API-ENDPOINTS §10.2 F-3042「keyword 非空按名称小写包含过滤」。
     * name 为空视为不匹配任何非空关键词。</p>
     *
     * @param keyword 关键词（调用方保证非空、已小写）
     * @return name 小写包含 keyword 时返回 true
     */
    public boolean nameContains(String keyword) {
        return name != null && name.toLowerCase().contains(keyword);
    }
}
