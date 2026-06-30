package com.nexa.domain.observability.nfr.vo;

/**
 * 容灾恢复目标（值对象，不可变）——F-5005 容灾演练与备份恢复，NFR-A08。
 *
 * <p>定义灾难恢复的 RTO（恢复时间目标）/ RPO（恢复点目标）上限并提供演练结果达标判定。
 * 领域规则来源：BACKLOG T-209 F-5005 验收「RTO≤30min 且 RPO≤5min」、API-ENDPOINTS §14.2 F-5005
 * （DB 主从+备份，基础设施/部署；Root 审批）。横切落地：备份恢复由基础设施承载，本值对象作为
 * 「容灾演练判定标准」，演练后用 {@link #isMet} 校验是否达标，本 BC 不挂端点。</p>
 *
 * @param rtoMinutes 恢复时间目标上限（分钟，默认 30）——故障到恢复服务的最长容忍时长
 * @param rpoMinutes 恢复点目标上限（分钟，默认 5）——最多可容忍丢失的数据时间窗
 */
public record BackupRecoveryObjective(long rtoMinutes, long rpoMinutes) {

    /** F-5005 默认 RTO 上限（分钟）。 */
    public static final long DEFAULT_RTO_MINUTES = 30;

    /** F-5005 默认 RPO 上限（分钟）。 */
    public static final long DEFAULT_RPO_MINUTES = 5;

    /**
     * 紧凑构造校验：RTO/RPO 为正。
     *
     * @throws IllegalArgumentException 非正
     */
    public BackupRecoveryObjective {
        if (rtoMinutes <= 0 || rpoMinutes <= 0) {
            throw new IllegalArgumentException("RTO/RPO must be positive minutes");
        }
    }

    /**
     * 契约默认目标（F-5005：RTO≤30min，RPO≤5min）。
     *
     * @return 默认容灾目标
     */
    public static BackupRecoveryObjective contractDefault() {
        return new BackupRecoveryObjective(DEFAULT_RTO_MINUTES, DEFAULT_RPO_MINUTES);
    }

    /**
     * 判定一次容灾演练结果是否达标。
     *
     * <p>领域规则：实测恢复时间 ≤ RTO <b>且</b> 实测数据丢失窗 ≤ RPO 才算达标（两条都过）。</p>
     *
     * @param observedRtoMinutes 实测恢复时间（分钟）
     * @param observedRpoMinutes 实测数据丢失窗（分钟）
     * @return 达标返回 {@code true}
     */
    public boolean isMet(long observedRtoMinutes, long observedRpoMinutes) {
        return observedRtoMinutes <= rtoMinutes && observedRpoMinutes <= rpoMinutes;
    }
}
