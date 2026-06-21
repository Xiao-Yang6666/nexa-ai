package com.nexa.task.application;

import com.nexa.task.application.port.RefundPort;
import com.nexa.task.domain.model.Task;
import com.nexa.task.domain.vo.BillingContext;
import com.nexa.task.domain.vo.RefundResult;
import org.springframework.stereotype.Service;

/**
 * 任务退款/差额结算用例（应用层，F-2009 计费上下文重算）。
 *
 * <p>用例编排：任务到终态后，调聚合 {@link Task#settleRefund} 按计费上下文重算退款（领域规则在聚合：
 * 按次跳过 / 成功差额 / 失败全退），再经 {@link RefundPort} 落账（按 billing_source 分流到订阅/钱包）。
 * 本用例不含计费规则（在聚合），也不含落账细节（在 port 实现），只做编排（薄）。</p>
 *
 * <p><b>CAS 守卫</b>（PRD AT-4）：退款仅在任务终态 CAS 成功后由调用方触发（{@link AdvanceTaskUseCase}
 * 推进终态成功后调本用例），避免覆盖已自然完成的任务。{@link RefundResult.Type#SKIP} 时不落账。</p>
 */
@Service
public class SettleRefundUseCase {

    private final RefundPort refundPort;

    /** @param refundPort 退款落账端口（基础设施实现，按计费来源分流） */
    public SettleRefundUseCase(RefundPort refundPort) {
        this.refundPort = refundPort;
    }

    /**
     * 结算任务退款（F-2009）。
     *
     * @param task        已到终态的任务聚合
     * @param actualQuota 实际消耗配额（SUCCESS 态按真实 token 折算；FAILURE 忽略）
     * @return 退款结果（供日志/审计）
     */
    public RefundResult settle(Task task, long actualQuota) {
        RefundResult result = task.settleRefund(actualQuota);
        // SKIP（按次计费）无需落账，直接返回（PRD AT-4 §跳过差额结算）。
        if (result.type() == RefundResult.Type.SKIP || result.refundQuota() <= 0) {
            return result;
        }
        BillingContext ctx = task.billingContext();
        if (ctx != null && task.userId() != null) {
            // 按 billing_source 分流落账（订阅/钱包），实现细节在适配器，task BC 不感知。
            refundPort.refund(task.userId(), ctx, result);
        }
        return result;
    }
}
