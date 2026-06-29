package com.nexa.domain.observability.alert;

/**
 * 告警严重级别（值对象枚举，F-5011）。
 *
 * <p>用于告警分级与渠道路由策略（如 CRITICAL 走全渠道含 Bark 即时推送，WARNING 仅 Email/Webhook）。</p>
 */
public enum AlertSeverity {

    /** 警告（接近 SLO 边界）。 */
    WARNING,

    /** 严重（已明显超出 SLO，需即时处理）。 */
    CRITICAL
}
