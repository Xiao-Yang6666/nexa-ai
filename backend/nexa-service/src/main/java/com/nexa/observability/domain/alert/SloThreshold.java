package com.nexa.observability.domain.alert;

import com.nexa.observability.domain.exception.InvalidMetricException;

/**
 * SLO 阈值值对象（F-5011，充血——自带越界判定）。
 *
 * <p>封装一条 SLO 阈值规则：哪个指标（{@link SloMetric}）、阈值、达到 {@link AlertSeverity#CRITICAL} 的
 * 升级阈值。核心行为 {@link #evaluate(double)} 据指标越界方向（{@link SloMetric#breachWhenAbove()}）判定
 * 观测值是否越界及严重级别——判定规则在值对象上（充血，backend-engineer §2.2），不散落到调用方。</p>
 *
 * <p>不可变、按值相等。阈值非法（NaN / critical 比 warning 更宽松）在构造期即拒。</p>
 *
 * <p>领域规则来源：NFR-O03「错误率/额度/限流/延迟超 SLO 告警」。</p>
 *
 * @param metric          监控指标
 * @param warningValue    告警阈值（达 WARNING）
 * @param criticalValue   升级阈值（达 CRITICAL；须比 warning 更严格，方向同 metric）
 */
public record SloThreshold(SloMetric metric, double warningValue, double criticalValue) {

    public SloThreshold {
        if (metric == null) {
            throw new InvalidMetricException("SLO threshold metric must not be null");
        }
        if (Double.isNaN(warningValue) || Double.isNaN(criticalValue)) {
            throw new InvalidMetricException("SLO threshold values must not be NaN");
        }
        // critical 必须比 warning 更严格（同越界方向上更靠近/超过）。方向错配是配置 bug，构造期即拒。
        if (metric.breachWhenAbove() && criticalValue < warningValue) {
            throw new InvalidMetricException(
                    "for above-breach metric, criticalValue must be >= warningValue");
        }
        if (!metric.breachWhenAbove() && criticalValue > warningValue) {
            throw new InvalidMetricException(
                    "for below-breach metric, criticalValue must be <= warningValue");
        }
    }

    /**
     * 评估观测值是否越界及严重级别。
     *
     * <p>越界方向由 {@link SloMetric#breachWhenAbove()} 决定：above 类「值 &gt; 阈值」越界，below 类
     * 「值 &lt; 阈值」越界。先判 critical 再判 warning（critical 更严格）。</p>
     *
     * @param observed 观测值
     * @return {@link AlertSeverity#CRITICAL}/{@link AlertSeverity#WARNING} 表示越界级别；
     *         {@code null} 表示未越界（在 SLO 内）
     */
    public AlertSeverity evaluate(double observed) {
        if (metric.breachWhenAbove()) {
            if (observed > criticalValue) {
                return AlertSeverity.CRITICAL;
            }
            if (observed > warningValue) {
                return AlertSeverity.WARNING;
            }
        } else {
            if (observed < criticalValue) {
                return AlertSeverity.CRITICAL;
            }
            if (observed < warningValue) {
                return AlertSeverity.WARNING;
            }
        }
        return null;
    }

    /**
     * 是否越界（不关心级别的查询版）。
     *
     * @param observed 观测值
     * @return 越界返回 {@code true}
     */
    public boolean isBreached(double observed) {
        return evaluate(observed) != null;
    }
}
