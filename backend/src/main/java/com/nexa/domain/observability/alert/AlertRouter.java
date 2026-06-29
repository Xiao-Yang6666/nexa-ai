package com.nexa.domain.observability.alert;

import java.util.EnumSet;
import java.util.Set;

/**
 * 告警渠道路由领域服务（F-5011，无状态领域服务）。
 *
 * <p>据告警严重级别决定送达哪些渠道（NFR-O03 多渠道告警编排）。路由策略是<b>跨渠道的领域规则</b>，不属于
 * 任一单渠道，故放领域服务（backend-engineer §2.4 跨聚合/跨值对象逻辑入领域服务）：
 * <ul>
 *   <li>{@link AlertSeverity#CRITICAL} → 全渠道（Email + Webhook + Bark），含即时推送 Bark 确保触达；</li>
 *   <li>{@link AlertSeverity#WARNING} → Email + Webhook（非即时渠道，避免告警疲劳）。</li>
 * </ul>
 * 实际启用哪些渠道还受配置（F-4037 通知设置）约束——本服务给出「该级别<b>应</b>路由的渠道集」，
 * 调用方（dispatcher）再与已配置启用的渠道取交集。</p>
 */
public final class AlertRouter {

    /**
     * 据严重级别给出应路由的渠道集。
     *
     * @param severity 告警严重级别
     * @return 应送达的渠道集（不可变副本语义，调用方可安全使用）
     */
    public Set<AlertChannel> channelsFor(AlertSeverity severity) {
        if (severity == AlertSeverity.CRITICAL) {
            return EnumSet.of(AlertChannel.EMAIL, AlertChannel.WEBHOOK, AlertChannel.BARK);
        }
        // WARNING：非即时渠道，避免 Bark 推送疲劳。
        return EnumSet.of(AlertChannel.EMAIL, AlertChannel.WEBHOOK);
    }
}
