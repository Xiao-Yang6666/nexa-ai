package com.nexa.domain.nfr.vo;

/**
 * 网关附加延迟预算（值对象，不可变）——F-5001 网关附加延迟与转发开销埋点，NFR-P01/P02/P06。
 *
 * <p>定义「网关自身引入的附加延迟」（不含上游推理耗时）的性能预算阈值，并提供达标判定。
 * 领域规则来源：BACKLOG T-205 F-5001 验收「p50≤15ms 且 p99≤60ms 可在指标系统查询」、
 * API-ENDPOINTS §14.2 F-5001。把阈值与达标判定收敛为值对象，供指标系统/压测门禁（F-5002）
 * 与监控告警统一引用，避免阈值散落（充血，backend-engineer §2.2/§2.4）。</p>
 *
 * <p>横切落地形态：本值对象是「埋点指标的判定标准」——relay 链路埋点（基础设施）采集网关附加延迟分位数后，
 * 由指标系统/压测门禁用 {@link #isMet} 判定是否达标。本 BC 不挂 REST 端点（F-5001 标「横切约束，无独立端点」），
 * 指标导出复用 {@code /metrics}（F-5010）。</p>
 *
 * @param p50Millis p50 附加延迟上限（毫秒，默认 15）
 * @param p99Millis p99 附加延迟上限（毫秒，默认 60）
 */
public record LatencyBudget(long p50Millis, long p99Millis) {

    /** F-5001 默认 p50 附加延迟上限（毫秒）。 */
    public static final long DEFAULT_P50_MILLIS = 15;

    /** F-5001 默认 p99 附加延迟上限（毫秒）。 */
    public static final long DEFAULT_P99_MILLIS = 60;

    /**
     * 紧凑构造校验：阈值为正且 p99 ≥ p50（分位数单调）。
     *
     * @throws IllegalArgumentException 阈值非正或 p99 &lt; p50
     */
    public LatencyBudget {
        if (p50Millis <= 0 || p99Millis <= 0) {
            throw new IllegalArgumentException("latency budget thresholds must be positive");
        }
        if (p99Millis < p50Millis) {
            throw new IllegalArgumentException("p99 budget must be >= p50 budget");
        }
    }

    /**
     * 契约默认预算（F-5001：p50≤15ms，p99≤60ms）。
     *
     * @return 默认延迟预算
     */
    public static LatencyBudget contractDefault() {
        return new LatencyBudget(DEFAULT_P50_MILLIS, DEFAULT_P99_MILLIS);
    }

    /**
     * 判定一组实测分位数是否在预算内（达标）。
     *
     * <p>领域规则：实测 p50 ≤ 预算 p50 <b>且</b> 实测 p99 ≤ 预算 p99 才算达标（两条都过）。</p>
     *
     * @param observedP50Millis 实测 p50 附加延迟（毫秒）
     * @param observedP99Millis 实测 p99 附加延迟（毫秒）
     * @return 达标返回 {@code true}
     */
    public boolean isMet(long observedP50Millis, long observedP99Millis) {
        return observedP50Millis <= p50Millis && observedP99Millis <= p99Millis;
    }
}
