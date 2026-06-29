package com.nexa.domain.observability.alert;

/**
 * 告警送达渠道（值对象枚举，F-5011）。
 *
 * <p>对齐 NFR-O03「渠道错误率/额度/限流/延迟超 SLO 经 Email/Webhook/Bark 告警」。每个枚举值对应一种
 * 外部通知载体，基础设施层为每种提供 {@code AlertNotifierPort} 实现（Email SMTP / Webhook HTTP POST /
 * Bark 推送）。配置复用通知设置（F-4037 NotificationSettings）。</p>
 */
public enum AlertChannel {

    /** 邮件告警（SMTP）。 */
    EMAIL,

    /** Webhook 告警（HTTP POST 到配置的回调地址）。 */
    WEBHOOK,

    /** Bark 推送告警（iOS 推送）。 */
    BARK
}
