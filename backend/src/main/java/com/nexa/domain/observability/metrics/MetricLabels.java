package com.nexa.domain.observability.metrics;

import com.nexa.domain.observability.exception.InvalidMetricException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Prometheus 维度标签集值对象（F-5010 RED 指标的维度，如 channel/model）。
 *
 * <p>承载一组有序、不可变的标签键值对（{@code {channel="c1",model="gpt-4o"}}），守护两条规范：
 * ① 标签名符合 Prometheus 规范 {@code [a-zA-Z_][a-zA-Z0-9_]*}；② 标签值做转义（{@code \\ }、{@code "}、
 * 换行）后才能进导出文本，避免破坏抓取格式或被注入。按值相等（值对象，backend-engineer §2.4）。</p>
 *
 * <p>领域规则来源：NFR-O01/O02「含维度标签」；Prometheus exposition format 标签规范。
 * 维度标签是 RED 指标可按渠道/模型下钻的关键，故标签集是<b>指标身份的一部分</b>（同名不同标签 = 不同时间序列）。</p>
 */
public final class MetricLabels {

    /** Prometheus 合法标签名正则。 */
    private static final Pattern VALID_LABEL = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    /** 空标签集（无维度指标，如全局计数）。 */
    public static final MetricLabels EMPTY = new MetricLabels(new LinkedHashMap<>());

    private final Map<String, String> labels;

    private MetricLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    /**
     * 从有序键值对构造标签集（保留插入顺序，便于稳定导出 + 测试可断言）。
     *
     * @param ordered 有序标签键值对（键为标签名，值为标签值；值可空，空按空串处理）
     * @return 标签集值对象
     * @throws InvalidMetricException 任一标签名非法
     */
    public static MetricLabels of(Map<String, String> ordered) {
        if (ordered == null || ordered.isEmpty()) {
            return EMPTY;
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : ordered.entrySet()) {
            String name = e.getKey();
            if (name == null || !VALID_LABEL.matcher(name).matches()) {
                throw new InvalidMetricException("illegal prometheus label name: " + name);
            }
            copy.put(name, e.getValue() == null ? "" : e.getValue());
        }
        return new MetricLabels(copy);
    }

    /**
     * 便捷构造单标签集。
     *
     * @param name  标签名
     * @param value 标签值
     * @return 标签集
     */
    public static MetricLabels single(String name, String value) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(name, value);
        return of(m);
    }

    /**
     * 便捷构造双标签集（RED 指标常用 channel + model）。
     *
     * @param n1 标签1名
     * @param v1 标签1值
     * @param n2 标签2名
     * @param v2 标签2值
     * @return 标签集
     */
    public static MetricLabels pair(String n1, String v1, String n2, String v2) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(n1, v1);
        m.put(n2, v2);
        return of(m);
    }

    /** @return 是否无维度标签 */
    public boolean isEmpty() {
        return labels.isEmpty();
    }

    /**
     * 渲染为 Prometheus 标签段 {@code {k1="v1",k2="v2"}}（含转义），空集返回空串。
     *
     * @return 标签段文本（含花括号；空集为空串）
     */
    public String render() {
        if (labels.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : labels.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append(e.getKey()).append("=\"").append(escape(e.getValue())).append('"');
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Prometheus 标签值转义：反斜杠、双引号、换行（防破坏导出格式/注入）。
     *
     * @param raw 原始标签值
     * @return 转义后的值
     */
    private static String escape(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MetricLabels other)) return false;
        return labels.equals(other.labels);
    }

    @Override
    public int hashCode() {
        return labels.hashCode();
    }

    @Override
    public String toString() {
        return render();
    }
}
