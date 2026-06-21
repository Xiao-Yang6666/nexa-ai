package com.nexa.observability.domain.alert;

import java.util.Optional;

/**
 * SLO 越界检测领域服务（F-5011，无状态领域服务）。
 *
 * <p>把「观测值 vs SLO 阈值」的越界判定 + 告警构造收敛到一处：给定渠道维度、阈值规则与观测值，越界则产出
 * {@link Alert}，否则空。判定逻辑本身在 {@link SloThreshold#evaluate(double)}（值对象充血），本服务负责
 * 把判定结果<b>编织成告警事件</b>（带渠道维度 + 突破的阈值），是跨值对象的领域服务（backend-engineer §2.4）。</p>
 *
 * <p>领域规则来源：NFR-O03「渠道错误率/额度/限流/延迟超 SLO 经 Email/Webhook/Bark 告警」。</p>
 */
public final class SloEvaluator {

    /**
     * 评估单条渠道维度的某指标观测值，越界则产出告警。
     *
     * @param channelLabel 渠道维度标识（如 channel id；全局用 "global"）
     * @param threshold    SLO 阈值规则
     * @param observed     观测值
     * @return 越界时返回告警；在 SLO 内返回 {@link Optional#empty()}
     */
    public Optional<Alert> evaluate(String channelLabel, SloThreshold threshold, double observed) {
        AlertSeverity severity = threshold.evaluate(observed);
        if (severity == null) {
            return Optional.empty();
        }
        // 突破的阈值按级别取：CRITICAL 报 critical 阈值，WARNING 报 warning 阈值（通知正文据此显示）。
        double breached = severity == AlertSeverity.CRITICAL
                ? threshold.criticalValue()
                : threshold.warningValue();
        return Optional.of(Alert.of(channelLabel, threshold.metric(), observed, breached, severity));
    }
}
