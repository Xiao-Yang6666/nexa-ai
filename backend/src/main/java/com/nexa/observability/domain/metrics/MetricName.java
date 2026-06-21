package com.nexa.observability.domain.metrics;

import com.nexa.observability.domain.exception.InvalidMetricException;

import java.util.regex.Pattern;

/**
 * Prometheus 指标名值对象（F-5010）。
 *
 * <p>守护 Prometheus 指标命名规范 {@code [a-zA-Z_:][a-zA-Z0-9_:]*}（不可变、按值相等，值对象）。
 * RED 指标采用 {@code <namespace>_<name>_<suffix>} 约定（如 {@code nexa_relay_requests_total}）。
 * 命名非法在构造期即拒（{@link InvalidMetricException}），避免脏指标名渗入导出文本破坏抓取方解析。</p>
 *
 * <p>领域规则来源：NFR-O01/O02「暴露按渠道/模型的请求率/错误率/延迟与额度速率，含维度标签」；
 * Prometheus exposition format 规范。</p>
 */
public final class MetricName {

    /** Prometheus 合法指标名正则。 */
    private static final Pattern VALID = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*");

    private final String value;

    private MetricName(String value) {
        this.value = value;
    }

    /**
     * 构造并校验指标名。
     *
     * @param value 指标名
     * @return 指标名值对象
     * @throws InvalidMetricException 名为空或不符合 Prometheus 命名规范
     */
    public static MetricName of(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidMetricException("metric name must not be blank");
        }
        if (!VALID.matcher(value).matches()) {
            throw new InvalidMetricException("illegal prometheus metric name: " + value);
        }
        return new MetricName(value);
    }

    /** @return 指标名字符串 */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MetricName other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
