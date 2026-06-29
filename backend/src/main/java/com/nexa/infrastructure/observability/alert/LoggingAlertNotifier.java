package com.nexa.infrastructure.observability.alert;

import com.nexa.application.observability.port.AlertNotifierPort;
import com.nexa.domain.observability.alert.Alert;
import com.nexa.domain.observability.alert.AlertChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * 告警通知端口的日志桩实现（基础设施层，F-5011）。
 *
 * <p>本期为骨架实现：把告警以结构化日志记录，渠道启用状态默认全开（便于编排逻辑端到端可验证）。真实
 * Email(SMTP)/Webhook(HTTP POST)/Bark 推送实现待接通配置（F-4037 NotificationSettings）后注入——届时
 * 替换本类或新增按渠道分发的复合实现，{@link com.nexa.application.observability.DispatchAlertsUseCase}
 * 零改动（依赖 {@link AlertNotifierPort} 端口，DDD §2.3 可替换实现）。</p>
 *
 * <p>不吞错：真实实现送达失败应 wrap 抛出携带渠道上下文的异常（backend-engineer §3.2）；本桩仅记日志。</p>
 */
@Component
public class LoggingAlertNotifier implements AlertNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlertNotifier.class);

    /** 本期默认启用的渠道（真实实现据 F-4037 配置动态返回）。 */
    private static final Set<AlertChannel> ENABLED =
            EnumSet.of(AlertChannel.EMAIL, AlertChannel.WEBHOOK, AlertChannel.BARK);

    /** {@inheritDoc} */
    @Override
    public void notify(AlertChannel channel, Alert alert) {
        // 结构化告警日志（正文已脱敏）；真实渠道送达待 F-4037 配置接通后实现。
        log.warn("alert dispatched channel={} severity={} message={}",
                channel, alert.severity(), alert.renderMessage());
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEnabled(AlertChannel channel) {
        return ENABLED.contains(channel);
    }
}
