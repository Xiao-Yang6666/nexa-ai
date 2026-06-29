package com.nexa.domain.observability.metrics;

import com.nexa.domain.observability.exception.InvalidMetricException;
import com.nexa.infrastructure.observability.metrics.PrometheusTextRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RED 指标注册表 + Prometheus 渲染单测（纯 JUnit，F-5010）。
 *
 * <p>覆盖：① 维度标签命名校验 + 渲染转义；② counter/histogram 累计与 RED 维度区分（channel/model）；
 * ③ Prometheus 文本格式（TYPE 行去重、histogram bucket/sum/count、le 标签合并）。
 * 渲染器虽属 infra，但无框架依赖可直接 new 单测。</p>
 */
@DisplayName("RED 指标 + Prometheus 渲染（F-5010）")
class MetricsTest {

    private final PrometheusTextRenderer renderer = new PrometheusTextRenderer();

    // ---------- MetricName / MetricLabels ----------

    @DisplayName("MetricName: 合法名通过，非法名拒绝")
    @Test
    void metricNameValidation() {
        assertEquals("nexa_relay_requests_total", MetricName.of("nexa_relay_requests_total").value());
        assertThrows(InvalidMetricException.class, () -> MetricName.of("9bad"));
        assertThrows(InvalidMetricException.class, () -> MetricName.of("has space"));
        assertThrows(InvalidMetricException.class, () -> MetricName.of(""));
    }

    @DisplayName("MetricLabels: 保序渲染 + 标签值转义")
    @Test
    void labelsRenderAndEscape() {
        assertEquals("{channel=\"c1\",model=\"gpt-4o\"}",
                MetricLabels.pair("channel", "c1", "model", "gpt-4o").render());
        // 转义双引号/反斜杠
        assertEquals("{model=\"a\\\"b\"}", MetricLabels.single("model", "a\"b").render());
        assertTrue(MetricLabels.EMPTY.isEmpty());
        assertEquals("", MetricLabels.EMPTY.render());
    }

    @DisplayName("MetricLabels: 非法标签名拒绝")
    @Test
    void labelNameValidation() {
        assertThrows(InvalidMetricException.class, () -> MetricLabels.single("bad-name", "v"));
    }

    @DisplayName("MetricKey: 同名不同标签 = 不同序列身份（按值相等）")
    @Test
    void keyIdentity() {
        MetricKey k1 = MetricKey.of("m", MetricLabels.single("channel", "a"));
        MetricKey k2 = MetricKey.of("m", MetricLabels.single("channel", "a"));
        MetricKey k3 = MetricKey.of("m", MetricLabels.single("channel", "b"));
        assertEquals(k1, k2);
        assertFalse(k1.equals(k3));
    }

    // ---------- MetricRegistry RED 累计 ----------

    @DisplayName("MetricRegistry: recordRequest 按 channel/model 维度累计请求/错误/额度/延迟")
    @Test
    void recordRequestAccumulates() {
        MetricRegistry reg = new MetricRegistry();
        reg.recordRequest("c1", "gpt-4o", false, 0.012, 100);
        reg.recordRequest("c1", "gpt-4o", true, 0.5, 200);
        reg.recordRequest("c2", "claude", false, 0.02, 50);

        List<MetricSnapshot> snaps = reg.snapshot();
        // c1/gpt-4o 请求 2、错误 1、额度 300；c2/claude 请求 1
        double c1Requests = scalarValue(snaps, MetricRegistry.REQUESTS_TOTAL, "c1", "gpt-4o");
        double c1Errors = scalarValue(snaps, MetricRegistry.ERRORS_TOTAL, "c1", "gpt-4o");
        double c1Quota = scalarValue(snaps, MetricRegistry.QUOTA_SPENT_TOTAL, "c1", "gpt-4o");
        double c2Requests = scalarValue(snaps, MetricRegistry.REQUESTS_TOTAL, "c2", "claude");
        assertEquals(2d, c1Requests);
        assertEquals(1d, c1Errors);
        assertEquals(300d, c1Quota);
        assertEquals(1d, c2Requests);
    }

    @DisplayName("MetricRegistry: counter 负增量拒绝（单调递增不变量）")
    @Test
    void counterMonotonic() {
        MetricRegistry reg = new MetricRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> reg.incCounter(MetricKey.of("m"), -1));
    }

    @DisplayName("MetricRegistry: 空白维度兜底 unknown，避免空标签归并错乱")
    @Test
    void blankDimensionFallsBack() {
        MetricRegistry reg = new MetricRegistry();
        reg.recordRequest(null, "", false, 0.01, 0);
        double v = scalarValue(reg.snapshot(), MetricRegistry.REQUESTS_TOTAL, "unknown", "unknown");
        assertEquals(1d, v);
    }

    // ---------- Histogram + Prometheus 渲染 ----------

    @DisplayName("MetricRegistry: histogram 累积桶 + sum/count 正确")
    @Test
    void histogramBuckets() {
        MetricRegistry reg = new MetricRegistry();
        // 0.012s 落 <=0.015 桶；0.5s 落 <=0.5 桶
        reg.recordRequest("c1", "m", false, 0.012, 0);
        reg.recordRequest("c1", "m", false, 0.5, 0);

        MetricSnapshot hist = reg.snapshot().stream()
                .filter(s -> s.type() == MetricType.HISTOGRAM)
                .findFirst().orElseThrow();
        assertEquals(2L, hist.count());
        assertEquals(0.512d, hist.sum(), 1e-9);
        // +Inf 桶累积 = 2（含全部观测）
        MetricSnapshot.BucketCount inf = hist.buckets().get(hist.buckets().size() - 1);
        assertTrue(Double.isInfinite(inf.upperBound()));
        assertEquals(2L, inf.cumulative());
    }

    @DisplayName("PrometheusTextRenderer: counter 渲染 name{labels} value + TYPE 行")
    @Test
    void renderCounter() {
        MetricRegistry reg = new MetricRegistry();
        reg.incCounter(MetricKey.of(MetricRegistry.REQUESTS_TOTAL,
                MetricLabels.pair("channel", "c1", "model", "gpt-4o")), 3);
        String text = renderer.render(reg.snapshot());
        assertTrue(text.contains("# TYPE nexa_relay_requests_total counter"));
        assertTrue(text.contains("nexa_relay_requests_total{channel=\"c1\",model=\"gpt-4o\"} 3"));
    }

    @DisplayName("PrometheusTextRenderer: histogram 渲染 _bucket{le} / _sum / _count")
    @Test
    void renderHistogram() {
        MetricRegistry reg = new MetricRegistry();
        reg.observeHistogram(MetricKey.of(MetricRegistry.DURATION_SECONDS,
                MetricLabels.single("channel", "c1")), 0.012);
        String text = renderer.render(reg.snapshot());
        assertTrue(text.contains("# TYPE nexa_relay_request_duration_seconds histogram"));
        assertTrue(text.contains("nexa_relay_request_duration_seconds_bucket{channel=\"c1\",le=\"+Inf\"} 1"));
        assertTrue(text.contains("nexa_relay_request_duration_seconds_count{channel=\"c1\"} 1"));
        assertTrue(text.contains("nexa_relay_request_duration_seconds_sum{channel=\"c1\"}"));
    }

    /** 取某 counter/gauge 序列在 channel/model 维度下的标量值。 */
    private double scalarValue(List<MetricSnapshot> snaps, String name, String channel, String model) {
        MetricKey key = MetricKey.of(name, MetricLabels.pair("channel", channel, "model", model));
        return snaps.stream()
                .filter(s -> s.key().equals(key))
                .map(MetricSnapshot::value)
                .findFirst()
                .orElse(Double.NaN);
    }
}
