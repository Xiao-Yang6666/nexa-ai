package com.nexa.nfr.domain.vo;

/**
 * 吞吐与并发压测基准（值对象，不可变）——F-5002 性能压测与基准门禁，NFR-P04/P05。
 *
 * <p>定义发布门禁的性能基准：稳态吞吐下限、并发长连接下限、可接受错误率上限，并提供达标判定。
 * 领域规则来源：BACKLOG T-206 F-5002 验收「稳态 ≥1500 req/s 且 ≥5000 并发长连接错误率 &lt;0.1%」、
 * API-ENDPOINTS §14.2 F-5002「失败阻断发布」。横切落地：CI/发布流程（部署）跑压测后用 {@link #passes}
 * 判定是否放行发布，本 BC 不挂 REST 端点。</p>
 *
 * @param minThroughputRps      稳态吞吐下限（req/s，默认 1500）
 * @param minConcurrentConns    并发长连接下限（默认 5000）
 * @param maxErrorRate          可接受错误率上限（小数，默认 0.001 = 0.1%）
 */
public record ThroughputBenchmark(long minThroughputRps,
                                  long minConcurrentConns,
                                  double maxErrorRate) {

    /** F-5002 默认稳态吞吐下限（req/s）。 */
    public static final long DEFAULT_MIN_THROUGHPUT_RPS = 1500;

    /** F-5002 默认并发长连接下限。 */
    public static final long DEFAULT_MIN_CONCURRENT_CONNS = 5000;

    /** F-5002 默认错误率上限（0.1%）。 */
    public static final double DEFAULT_MAX_ERROR_RATE = 0.001;

    /**
     * 紧凑构造校验：下限为正、错误率在 [0,1)。
     *
     * @throws IllegalArgumentException 参数越界
     */
    public ThroughputBenchmark {
        if (minThroughputRps <= 0 || minConcurrentConns <= 0) {
            throw new IllegalArgumentException("throughput/concurrency floors must be positive");
        }
        if (maxErrorRate < 0 || maxErrorRate >= 1) {
            throw new IllegalArgumentException("max error rate must be in [0, 1)");
        }
    }

    /**
     * 契约默认基准（F-5002）。
     *
     * @return 默认压测基准
     */
    public static ThroughputBenchmark contractDefault() {
        return new ThroughputBenchmark(
                DEFAULT_MIN_THROUGHPUT_RPS, DEFAULT_MIN_CONCURRENT_CONNS, DEFAULT_MAX_ERROR_RATE);
    }

    /**
     * 判定一次压测结果是否通过门禁（放行发布）。
     *
     * <p>领域规则：实测稳态吞吐 ≥ 下限 <b>且</b> 实测并发 ≥ 下限 <b>且</b> 实测错误率 ≤ 上限，
     * 三条全过才放行；任一未达 → 阻断发布（F-5002「失败阻断发布」）。</p>
     *
     * @param observedThroughputRps 实测稳态吞吐
     * @param observedConcurrency   实测并发长连接数
     * @param observedErrorRate     实测错误率（小数）
     * @return 通过返回 {@code true}
     */
    public boolean passes(long observedThroughputRps, long observedConcurrency, double observedErrorRate) {
        return observedThroughputRps >= minThroughputRps
                && observedConcurrency >= minConcurrentConns
                && observedErrorRate <= maxErrorRate;
    }
}
