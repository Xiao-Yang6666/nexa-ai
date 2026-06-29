package com.nexa.application.observability;

import com.nexa.application.observability.port.AlertNotifierPort;
import com.nexa.domain.observability.alert.Alert;
import com.nexa.domain.observability.alert.AlertChannel;
import com.nexa.domain.observability.alert.AlertRouter;
import com.nexa.domain.observability.alert.SloEvaluator;
import com.nexa.domain.observability.alert.SloThreshold;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/**
 * SLO 告警编排用例（应用层，F-5011，无独立 REST 端点）。
 *
 * <p>编排「检测越界 → 路由渠道 → 多渠道送达」（NFR-O03 多渠道告警编排），薄壳无业务规则：
 * <ol>
 *   <li>用 {@link SloEvaluator} 判定观测值是否越界并产出 {@link Alert}（判定规则在 domain）；</li>
 *   <li>用 {@link AlertRouter} 据严重级别取应路由渠道集（路由规则在 domain）；</li>
 *   <li>与配置启用的渠道（{@link AlertNotifierPort#isEnabled}）取交集后逐一送达。</li>
 * </ol>
 * 「横切/系统内部」能力（API-COVERAGE 标 REST=N）：本用例由 relay 链路/指标聚合循环/定时巡检在检测到
 * 渠道 SLO 越界时调用，不挂 HTTP 端点。配置复用通知设置（F-4037）。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §14.3 F-5011。</p>
 */
@Service
public class DispatchAlertsUseCase {

    private final SloEvaluator evaluator;
    private final AlertRouter router;
    private final AlertNotifierPort notifier;

    /**
     * @param evaluator SLO 越界检测领域服务
     * @param router    告警渠道路由领域服务
     * @param notifier  告警通知端口（infra 实现，按渠道送达 + 启用判定）
     */
    public DispatchAlertsUseCase(SloEvaluator evaluator, AlertRouter router, AlertNotifierPort notifier) {
        this.evaluator = evaluator;
        this.router = router;
        this.notifier = notifier;
    }

    /**
     * 评估一条渠道维度的某指标观测值，越界则路由并多渠道送达。
     *
     * @param channelLabel 渠道维度标识
     * @param threshold    SLO 阈值规则
     * @param observed     观测值
     * @return 是否触发了告警（越界并已尝试送达返回 {@code true}）
     */
    public boolean evaluateAndDispatch(String channelLabel, SloThreshold threshold, double observed) {
        Optional<Alert> maybe = evaluator.evaluate(channelLabel, threshold, observed);
        if (maybe.isEmpty()) {
            return false; // 在 SLO 内，不告警。
        }
        Alert alert = maybe.get();
        Set<AlertChannel> targets = router.channelsFor(alert.severity());
        for (AlertChannel channel : targets) {
            // 仅送达已配置启用的渠道（与 F-4037 通知设置取交集），未启用则跳过。
            if (notifier.isEnabled(channel)) {
                notifier.notify(channel, alert);
            }
        }
        return true;
    }
}
