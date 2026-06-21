package com.nexa.observability.domain.metrics;

/**
 * Prometheus 指标类型（值对象，F-5010）。
 *
 * <p>RED 方法学（Rate/Errors/Duration）落到 Prometheus 三类基础指标：
 * <ul>
 *   <li>{@link #COUNTER} —— 单调递增计数（请求数 requests_total、错误数 errors_total），算 Rate/Errors；</li>
 *   <li>{@link #GAUGE}   —— 可增可减瞬时值（额度消耗速率 quota_rate、在途请求 inflight）；</li>
 *   <li>{@link #HISTOGRAM} —— 分桶分布（请求延迟 duration_seconds），算 Duration 分位。</li>
 * </ul>
 * {@link #typeKeyword()} 给出 Prometheus {@code # TYPE} 行的类型关键字。</p>
 */
public enum MetricType {

    /** 单调递增计数器。 */
    COUNTER("counter"),

    /** 瞬时量规。 */
    GAUGE("gauge"),

    /** 直方图（分桶 + _sum + _count）。 */
    HISTOGRAM("histogram");

    private final String keyword;

    MetricType(String keyword) {
        this.keyword = keyword;
    }

    /** @return Prometheus {@code # TYPE} 行使用的类型关键字 */
    public String typeKeyword() {
        return keyword;
    }
}
