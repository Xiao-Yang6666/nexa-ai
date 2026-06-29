package com.nexa.domain.nfr.vo;

/**
 * SLA 月度可用性目标（值对象，不可变）——F-5004 SLA 月度可用性报表，NFR-A01。
 *
 * <p>定义数据面/控制面的月度可用性下限并提供达标判定与停机预算换算。领域规则来源：
 * BACKLOG T-208 F-5004 验收「数据面 ≥99.9% 控制面 ≥99.5% 可出报」、API-ENDPOINTS §14.2 F-5004（只读报表）。
 * 横切落地：uptime_kuma + perf_metrics 聚合（基础设施）出月度报表，用本值对象判定是否达标，本 BC 不挂端点。</p>
 *
 * @param dataPlaneTarget    数据面（转发/推理链路）可用性下限（小数，默认 0.999）
 * @param controlPlaneTarget 控制面（控制台/管理 API）可用性下限（小数，默认 0.995）
 */
public record AvailabilityTarget(double dataPlaneTarget, double controlPlaneTarget) {

    /** F-5004 默认数据面可用性下限（99.9%）。 */
    public static final double DEFAULT_DATA_PLANE = 0.999;

    /** F-5004 默认控制面可用性下限（99.5%）。 */
    public static final double DEFAULT_CONTROL_PLANE = 0.995;

    /** 一个自然月的近似分钟数（30 天），用于停机预算换算。 */
    private static final long MINUTES_PER_MONTH = 30L * 24 * 60;

    /**
     * 紧凑构造校验：两个目标都在 (0, 1]。
     *
     * @throws IllegalArgumentException 目标越界
     */
    public AvailabilityTarget {
        if (dataPlaneTarget <= 0 || dataPlaneTarget > 1
                || controlPlaneTarget <= 0 || controlPlaneTarget > 1) {
            throw new IllegalArgumentException("availability targets must be in (0, 1]");
        }
    }

    /**
     * 契约默认目标（F-5004：数据面 99.9% / 控制面 99.5%）。
     *
     * @return 默认可用性目标
     */
    public static AvailabilityTarget contractDefault() {
        return new AvailabilityTarget(DEFAULT_DATA_PLANE, DEFAULT_CONTROL_PLANE);
    }

    /**
     * 判定数据面实测可用性是否达标。
     *
     * @param observed 实测可用性（小数）
     * @return 达标返回 {@code true}
     */
    public boolean isDataPlaneMet(double observed) {
        return observed >= dataPlaneTarget;
    }

    /**
     * 判定控制面实测可用性是否达标。
     *
     * @param observed 实测可用性（小数）
     * @return 达标返回 {@code true}
     */
    public boolean isControlPlaneMet(double observed) {
        return observed >= controlPlaneTarget;
    }

    /**
     * 数据面每月允许的最大停机分钟数（停机预算）。
     *
     * <p>换算：{@code (1 - target) * 一月分钟数}。用于报表展示「本月已用/剩余停机预算」。</p>
     *
     * @return 数据面月度停机预算（分钟）
     */
    public long dataPlaneMonthlyDowntimeBudgetMinutes() {
        return Math.round((1.0 - dataPlaneTarget) * MINUTES_PER_MONTH);
    }
}
