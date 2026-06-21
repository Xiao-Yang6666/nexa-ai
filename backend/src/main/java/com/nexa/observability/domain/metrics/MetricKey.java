package com.nexa.observability.domain.metrics;

import java.util.Objects;

/**
 * 指标时间序列身份键（值对象，F-5010）。
 *
 * <p>Prometheus 中一条时间序列由「指标名 + 标签集」唯一确定（同名不同标签 = 不同序列）。本值对象组合
 * {@link MetricName} + {@link MetricLabels} 作为序列身份，用作注册表的聚合键，使「按渠道/模型下钻」的
 * RED 维度天然成为不同序列（NFR-O01/O02）。不可变、按值相等。</p>
 *
 * @param name   指标名
 * @param labels 维度标签集
 */
public record MetricKey(MetricName name, MetricLabels labels) {

    public MetricKey {
        Objects.requireNonNull(name, "metric name must not be null");
        Objects.requireNonNull(labels, "metric labels must not be null");
    }

    /**
     * 便捷构造（无维度标签）。
     *
     * @param name 指标名字符串
     * @return 身份键
     */
    public static MetricKey of(String name) {
        return new MetricKey(MetricName.of(name), MetricLabels.EMPTY);
    }

    /**
     * 便捷构造（带标签集）。
     *
     * @param name   指标名字符串
     * @param labels 标签集
     * @return 身份键
     */
    public static MetricKey of(String name, MetricLabels labels) {
        return new MetricKey(MetricName.of(name), labels);
    }
}
