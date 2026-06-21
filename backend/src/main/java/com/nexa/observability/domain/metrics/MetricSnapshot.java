package com.nexa.observability.domain.metrics;

import java.util.List;

/**
 * 单条指标序列的导出快照（值对象，F-5010）。
 *
 * <p>把注册表内部可变累加状态投影为一份不可变快照，供导出层渲染 Prometheus 文本（domain 不依赖导出
 * 格式细节，仅产出中性数据）。Counter/Gauge 用 {@code value}；Histogram 用 {@code buckets} +
 * {@code sum}/{@code count}。</p>
 *
 * @param key     序列身份键（名 + 标签）
 * @param type    指标类型
 * @param value   counter/gauge 的当前值（histogram 忽略，取 0）
 * @param buckets histogram 累积桶（histogram 专用，其余类型空列表）
 * @param sum     histogram 观测值总和（histogram 专用）
 * @param count   histogram 观测次数（histogram 专用）
 */
public record MetricSnapshot(
        MetricKey key,
        MetricType type,
        double value,
        List<BucketCount> buckets,
        double sum,
        long count
) {

    /**
     * 直方图单桶累积计数（值对象）。
     *
     * @param upperBound 桶上界（秒），{@code Double.POSITIVE_INFINITY} 表示 +Inf 桶
     * @param cumulative 累积到本上界的观测次数（Prometheus histogram 桶为累积计数）
     */
    public record BucketCount(double upperBound, long cumulative) {
    }

    /**
     * 构造 counter/gauge 快照。
     *
     * @param key   身份键
     * @param type  COUNTER 或 GAUGE
     * @param value 当前值
     * @return 快照
     */
    public static MetricSnapshot scalar(MetricKey key, MetricType type, double value) {
        return new MetricSnapshot(key, type, value, List.of(), 0d, 0L);
    }

    /**
     * 构造 histogram 快照。
     *
     * @param key     身份键
     * @param buckets 累积桶（按上界升序）
     * @param sum     观测值总和
     * @param count   观测次数
     * @return 快照
     */
    public static MetricSnapshot histogram(MetricKey key, List<BucketCount> buckets, double sum, long count) {
        return new MetricSnapshot(key, MetricType.HISTOGRAM, 0d, List.copyOf(buckets), sum, count);
    }
}
