package com.nexa.observability.domain.alert;

/**
 * SLO 监控指标类型（值对象枚举，F-5011）。
 *
 * <p>对齐 NFR-O03 告警触发维度「错误率/额度/限流/延迟」。每类有各自的阈值语义与「越界方向」：
 * <ul>
 *   <li>{@link #ERROR_RATE} —— 错误率（0~1），<b>超过</b>阈值触发；</li>
 *   <li>{@link #QUOTA_REMAINING} —— 剩余额度比例（0~1），<b>低于</b>阈值触发（额度将耗尽）；</li>
 *   <li>{@link #RATE_LIMIT_HITS} —— 单位时间限流命中次数，<b>超过</b>阈值触发；</li>
 *   <li>{@link #LATENCY_P99_MS} —— p99 延迟（毫秒），<b>超过</b>阈值触发（NFR-P02 p99&lt;=60ms）。</li>
 * </ul>
 * {@link #breachWhenAbove()} 标明越界方向，供 {@code SloThreshold} 统一判定。</p>
 */
public enum SloMetric {

    /** 错误率（超过阈值触发）。 */
    ERROR_RATE(true),

    /** 剩余额度比例（低于阈值触发）。 */
    QUOTA_REMAINING(false),

    /** 限流命中次数（超过阈值触发）。 */
    RATE_LIMIT_HITS(true),

    /** p99 延迟毫秒（超过阈值触发）。 */
    LATENCY_P99_MS(true);

    private final boolean breachWhenAbove;

    SloMetric(boolean breachWhenAbove) {
        this.breachWhenAbove = breachWhenAbove;
    }

    /**
     * @return {@code true} 表示「观测值 &gt; 阈值」触发；{@code false} 表示「观测值 &lt; 阈值」触发
     */
    public boolean breachWhenAbove() {
        return breachWhenAbove;
    }
}
