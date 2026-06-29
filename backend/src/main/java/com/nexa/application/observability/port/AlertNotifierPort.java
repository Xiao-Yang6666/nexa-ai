package com.nexa.application.observability.port;

import com.nexa.domain.observability.alert.Alert;
import com.nexa.domain.observability.alert.AlertChannel;

/**
 * 告警通知端口（应用层，DDD §2.3 接口抽象，F-5011）。
 *
 * <p>抽象「把一条告警经某渠道送达」这一副作用，使可观测应用层<b>不</b>感知 SMTP / HTTP webhook / Bark
 * 推送的具体实现。基础设施层为每种 {@link AlertChannel} 提供实现（或一个按渠道分发的复合实现），配置复用
 * 通知设置（F-4037 NotificationSettings）。单测可注入桩验证路由正确（不真发通知）。</p>
 *
 * <p>语义：发送失败应 wrap 抛出携带渠道上下文的异常（不吞错，backend-engineer §3.2），由调用方决定
 * 重试/降级——告警送达本身失败不应静默。</p>
 */
public interface AlertNotifierPort {

    /**
     * 经指定渠道送达一条告警。
     *
     * @param channel 送达渠道
     * @param alert   告警内容（{@link Alert#renderMessage()} 为通知正文，已脱敏）
     */
    void notify(AlertChannel channel, Alert alert);

    /**
     * 该渠道当前是否已配置启用（复用 F-4037 通知设置）。
     *
     * @param channel 渠道
     * @return 启用返回 {@code true}（未配置/禁用返回 {@code false}，dispatcher 跳过）
     */
    boolean isEnabled(AlertChannel channel);
}
