package com.nexa.observability.domain.alert;

import java.util.Objects;

/**
 * 告警聚合根（F-5011，充血模型）。
 *
 * <p>一条由 SLO 越界产生的告警事件：哪个渠道维度（{@code channelLabel}）、哪个指标（{@link SloMetric}）、
 * 观测值、阈值、严重级别、可读消息。由 {@code SloEvaluator} 检测越界后构造，交 {@code AlertDispatcher} 经
 * {@link AlertChannel} 路由送达。{@link #renderMessage()} 把告警渲染成对外通知正文（脱敏，不含上游凭证）。</p>
 *
 * <p>不变量：severity 非空、observed/threshold 非 NaN。一旦构造即代表「一条已确认越界的告警」（未越界由
 * SloEvaluator 返回空，不产生本对象）。零框架依赖，可纯 JUnit 单测。</p>
 *
 * <p>领域规则来源：NFR-O03。</p>
 */
public final class Alert {

    private final String channelLabel;
    private final SloMetric metric;
    private final double observed;
    private final double threshold;
    private final AlertSeverity severity;

    private Alert(String channelLabel, SloMetric metric, double observed,
                  double threshold, AlertSeverity severity) {
        this.channelLabel = channelLabel;
        this.metric = metric;
        this.observed = observed;
        this.threshold = threshold;
        this.severity = severity;
    }

    /**
     * 构造一条 SLO 越界告警。
     *
     * @param channelLabel 越界的渠道维度标识（如 channel id；全局则 "global"）
     * @param metric       越界指标
     * @param observed     观测值
     * @param threshold    被突破的阈值（按级别取 warning/critical 值）
     * @param severity     严重级别（非空）
     * @return 告警聚合
     * @throws IllegalArgumentException severity 为空或数值为 NaN
     */
    public static Alert of(String channelLabel, SloMetric metric, double observed,
                           double threshold, AlertSeverity severity) {
        Objects.requireNonNull(metric, "alert metric must not be null");
        Objects.requireNonNull(severity, "alert severity must not be null");
        if (Double.isNaN(observed) || Double.isNaN(threshold)) {
            throw new IllegalArgumentException("alert observed/threshold must not be NaN");
        }
        String label = (channelLabel == null || channelLabel.isBlank()) ? "global" : channelLabel.trim();
        return new Alert(label, metric, observed, threshold, severity);
    }

    /**
     * 渲染对外通知正文（脱敏，结构化可读）。
     *
     * @return 通知正文
     */
    public String renderMessage() {
        return "[" + severity + "] SLO breach on channel=" + channelLabel
                + " metric=" + metric
                + " observed=" + observed
                + " threshold=" + threshold;
    }

    /** @return 渠道维度标识 */
    public String channelLabel() {
        return channelLabel;
    }

    /** @return 越界指标 */
    public SloMetric metric() {
        return metric;
    }

    /** @return 观测值 */
    public double observed() {
        return observed;
    }

    /** @return 被突破的阈值 */
    public double threshold() {
        return threshold;
    }

    /** @return 严重级别 */
    public AlertSeverity severity() {
        return severity;
    }
}
