package com.nexa.observability.infrastructure.metrics;

import com.nexa.observability.domain.metrics.MetricKey;
import com.nexa.observability.domain.metrics.MetricSnapshot;
import com.nexa.observability.domain.metrics.MetricType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prometheus 文本格式渲染器（基础设施层，F-5010）。
 *
 * <p>把领域中性的 {@link MetricSnapshot} 列表渲染为 Prometheus exposition format（text/plain; version=0.0.4）：
 * 每个指标名输出一行 {@code # TYPE <name> <type>}，随后各序列样本行。Histogram 展开为 {@code _bucket{le=...}} +
 * {@code _sum} + {@code _count}（Prometheus histogram 约定）。渲染细节（格式/转义/类型行去重）属 infra 职责，
 * domain 不感知（DDD §2.1）。</p>
 *
 * <p>安全：仅渲染已脱敏的维度标签（channel id / 公开模型名 A，不含上游名 B / 凭证），标签值转义在
 * {@code MetricLabels} 内完成。</p>
 */
@Component
public class PrometheusTextRenderer {

    /** Prometheus 文本格式 Content-Type。 */
    public static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

    /**
     * 渲染快照列表为 Prometheus 文本。
     *
     * <p>同名指标的 {@code # TYPE} 行只输出一次（Prometheus 要求每指标名一个 TYPE 声明），随后该名下各
     * 标签序列样本行。</p>
     *
     * @param snapshots 指标快照列表
     * @return Prometheus 文本（UTF-8）
     */
    public String render(List<MetricSnapshot> snapshots) {
        StringBuilder sb = new StringBuilder();
        // 记录已输出 TYPE 行的指标名（同名多序列只声明一次类型）。
        Map<String, Boolean> typeEmitted = new LinkedHashMap<>();

        for (MetricSnapshot s : snapshots) {
            String name = s.key().name().value();
            if (!typeEmitted.containsKey(name)) {
                sb.append("# TYPE ").append(name).append(' ')
                        .append(s.type().typeKeyword()).append('\n');
                typeEmitted.put(name, Boolean.TRUE);
            }
            if (s.type() == MetricType.HISTOGRAM) {
                renderHistogram(sb, s);
            } else {
                renderScalar(sb, s);
            }
        }
        return sb.toString();
    }

    /**
     * 渲染 counter/gauge 单样本行：{@code name{labels} value}。
     */
    private void renderScalar(StringBuilder sb, MetricSnapshot s) {
        MetricKey key = s.key();
        sb.append(key.name().value())
                .append(key.labels().render())
                .append(' ')
                .append(formatDouble(s.value()))
                .append('\n');
    }

    /**
     * 渲染 histogram：各累积桶 {@code _bucket{le="..."}} + {@code _sum} + {@code _count}。
     *
     * <p>le 标签需并入原维度标签（channel/model）形成完整标签段；这里手工拼接，因 le 是 histogram 桶专属维度。</p>
     */
    private void renderHistogram(StringBuilder sb, MetricSnapshot s) {
        String name = s.key().name().value();
        String baseLabels = s.key().labels().render(); // {channel="..",model=".."} 或空串

        for (MetricSnapshot.BucketCount b : s.buckets()) {
            String le = Double.isInfinite(b.upperBound()) ? "+Inf" : formatDouble(b.upperBound());
            sb.append(name).append("_bucket")
                    .append(mergeLabel(baseLabels, "le", le))
                    .append(' ')
                    .append(b.cumulative())
                    .append('\n');
        }
        sb.append(name).append("_sum").append(baseLabels).append(' ')
                .append(formatDouble(s.sum())).append('\n');
        sb.append(name).append("_count").append(baseLabels).append(' ')
                .append(s.count()).append('\n');
    }

    /**
     * 把单个标签并入已渲染的标签段（用于 histogram 的 {@code le} 标签）。
     *
     * @param baseLabels 已渲染标签段（{@code {k="v"}} 或空串）
     * @param name       新增标签名
     * @param value      新增标签值（已是合法值，如 "+Inf"/数字字符串）
     * @return 合并后的标签段
     */
    private String mergeLabel(String baseLabels, String name, String value) {
        String pair = name + "=\"" + value + "\"";
        if (baseLabels.isEmpty()) {
            return "{" + pair + "}";
        }
        // baseLabels 形如 {a="1",b="2"}；在末尾 } 前插入新标签。
        return baseLabels.substring(0, baseLabels.length() - 1) + "," + pair + "}";
    }

    /**
     * 格式化 double 为 Prometheus 友好文本（整数去尾零，避免 1.0 这类噪声）。
     *
     * @param v 数值
     * @return 文本
     */
    private String formatDouble(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        return Double.toString(v);
    }
}
