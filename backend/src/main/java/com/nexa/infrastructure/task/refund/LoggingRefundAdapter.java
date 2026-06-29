package com.nexa.infrastructure.task.refund;

import com.nexa.application.task.port.RefundPort;
import com.nexa.domain.task.vo.BillingContext;
import com.nexa.domain.task.vo.RefundResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 退款落账端口的占位适配器（基础设施层，F-2009 退款落账接线点）。
 *
 * <p>W3 本 wave 提供可注入的默认实现以打通编译与依赖装配；<b>真实落账</b>（订阅项
 * SubscriptionPreConsumeRecord refunded / 令牌钱包额度退回）需对接 billing/account BC，在后续 wave
 * 由真实适配器替换（PRD AT-4 §BillingSource 分流）。当前实现仅结构化记录退款意图供观测/审计，
 * 不实际改账（避免在依赖未就绪时误扣减/误退）。</p>
 *
 * <p><b>接线约定</b>：后续接 billing 时，把本类替换为调用 billing 应用服务的适配器（按
 * {@link BillingContext#billingSource} 分流到订阅退款 / 钱包退款），或加 {@code @Primary} 的真实实现。</p>
 */
@Component
public class LoggingRefundAdapter implements RefundPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingRefundAdapter.class);

    /** {@inheritDoc} */
    @Override
    public void refund(int userId, BillingContext context, RefundResult result) {
        // 结构化记录退款意图（观测/审计）；真实落账待后续 wave 接 billing/account BC。
        log.info("task refund intent: user_id={}, source={}, type={}, refund_quota={} (NOT yet posted - pending billing wire-up)",
                userId,
                context == null || context.billingSource() == null ? "unknown" : context.billingSource().toWire(),
                result.type(),
                result.refundQuota());
    }
}
