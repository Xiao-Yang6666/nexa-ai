package com.nexa.domain.observability.alert;

import com.nexa.domain.observability.exception.InvalidMetricException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SLO 告警编排核心逻辑单测（纯 JUnit，F-5011）。
 *
 * <p>覆盖：① SloThreshold 越界判定（above/below 双方向 + 级别升级）；② SloEvaluator 产出告警；
 * ③ AlertRouter 按级别路由渠道（CRITICAL 全渠道含 Bark，WARNING 仅 Email/Webhook）。正常/边界/异常三类。</p>
 */
@DisplayName("SLO 告警编排（F-5011）")
class AlertTest {

    // ---------- SloThreshold 越界判定 ----------

    @DisplayName("错误率(above)：超 warning→WARNING，超 critical→CRITICAL，未超→null")
    @Test
    void errorRateAboveBreach() {
        SloThreshold t = new SloThreshold(SloMetric.ERROR_RATE, 0.05, 0.10);
        assertNull(t.evaluate(0.02));                          // 在 SLO 内
        assertEquals(AlertSeverity.WARNING, t.evaluate(0.07)); // 超 warning
        assertEquals(AlertSeverity.CRITICAL, t.evaluate(0.20));// 超 critical
        assertTrue(t.isBreached(0.07));
        assertFalse(t.isBreached(0.01));
    }

    @DisplayName("剩余额度(below)：低于 warning→WARNING，低于 critical→CRITICAL")
    @Test
    void quotaBelowBreach() {
        SloThreshold t = new SloThreshold(SloMetric.QUOTA_REMAINING, 0.20, 0.05);
        assertNull(t.evaluate(0.50));                           // 额度充足
        assertEquals(AlertSeverity.WARNING, t.evaluate(0.10));  // 低于 warning
        assertEquals(AlertSeverity.CRITICAL, t.evaluate(0.01)); // 低于 critical
    }

    @DisplayName("阈值方向错配 / NaN → 构造期拒绝")
    @Test
    void thresholdInvariants() {
        // above 指标 critical 必 >= warning
        assertThrows(InvalidMetricException.class,
                () -> new SloThreshold(SloMetric.ERROR_RATE, 0.10, 0.05));
        // below 指标 critical 必 <= warning
        assertThrows(InvalidMetricException.class,
                () -> new SloThreshold(SloMetric.QUOTA_REMAINING, 0.05, 0.20));
        assertThrows(InvalidMetricException.class,
                () -> new SloThreshold(SloMetric.LATENCY_P99_MS, Double.NaN, 1));
    }

    // ---------- SloEvaluator ----------

    @DisplayName("SloEvaluator：越界产出告警（带渠道维度 + 突破的级别阈值）")
    @Test
    void evaluatorProducesAlert() {
        SloEvaluator evaluator = new SloEvaluator();
        SloThreshold t = new SloThreshold(SloMetric.LATENCY_P99_MS, 60, 200);
        Optional<Alert> alert = evaluator.evaluate("c1", t, 300);
        assertTrue(alert.isPresent());
        assertEquals(AlertSeverity.CRITICAL, alert.get().severity());
        assertEquals("c1", alert.get().channelLabel());
        assertEquals(200d, alert.get().threshold()); // CRITICAL 报 critical 阈值
        assertEquals(SloMetric.LATENCY_P99_MS, alert.get().metric());
    }

    @DisplayName("SloEvaluator：未越界返回空")
    @Test
    void evaluatorNoBreach() {
        SloEvaluator evaluator = new SloEvaluator();
        SloThreshold t = new SloThreshold(SloMetric.LATENCY_P99_MS, 60, 200);
        assertTrue(evaluator.evaluate("c1", t, 30).isEmpty());
    }

    @DisplayName("Alert：空白渠道兜底 global，renderMessage 含级别/指标")
    @Test
    void alertRendering() {
        Alert a = Alert.of(null, SloMetric.ERROR_RATE, 0.3, 0.1, AlertSeverity.CRITICAL);
        assertEquals("global", a.channelLabel());
        assertTrue(a.renderMessage().contains("CRITICAL"));
        assertTrue(a.renderMessage().contains("ERROR_RATE"));
    }

    // ---------- AlertRouter ----------

    @DisplayName("AlertRouter：CRITICAL→全渠道含 Bark；WARNING→仅 Email/Webhook")
    @Test
    void routingBySeverity() {
        AlertRouter router = new AlertRouter();
        Set<AlertChannel> critical = router.channelsFor(AlertSeverity.CRITICAL);
        assertTrue(critical.contains(AlertChannel.EMAIL));
        assertTrue(critical.contains(AlertChannel.WEBHOOK));
        assertTrue(critical.contains(AlertChannel.BARK));

        Set<AlertChannel> warning = router.channelsFor(AlertSeverity.WARNING);
        assertTrue(warning.contains(AlertChannel.EMAIL));
        assertTrue(warning.contains(AlertChannel.WEBHOOK));
        assertFalse(warning.contains(AlertChannel.BARK));
    }
}
