package com.nexa.domain.ops.exception;

import com.nexa.sharedkernel.HttpAwareDomainException;

/**
 * 支付合规声明未确认或确认上下文非法（F-4030 + Compliance F-5021）。
 *
 * <p>领域规则来源：API-ENDPOINTS §9.5 POST /api/option/payment_compliance：
 * <ul>
 *   <li>{@code confirmed=false}→「请确认合规声明」（400 入参错误）；</li>
 *   <li>使用 access_token / API token（非 dashboard 会话）→「requires dashboard session」（403）。</li>
 * </ul>
 * 会话上下文限制（仅 dashboard）属越权语义 → 403；入参未确认属客户端错误 → 400。
 * 用单一异常携带不同 httpStatus 表达两种语义。</p>
 */
public class PaymentComplianceException extends HttpAwareDomainException {

    /**
     * @param httpStatus 400（未确认）或 403（会话上下文非法）
     * @param message    具体原因（对齐契约文案）
     */
    public PaymentComplianceException(int httpStatus, String message) {
        super("OPS_PAYMENT_COMPLIANCE", httpStatus, message);
    }

    /**
     * 未确认合规声明（confirmed != true）→ 400。
     *
     * @return 未确认异常
     */
    public static PaymentComplianceException notConfirmed() {
        return new PaymentComplianceException(400, "请确认合规声明");
    }

    /**
     * 非 dashboard 会话（access_token/API token）禁止确认 → 403。
     *
     * @return 会话上下文非法异常
     */
    public static PaymentComplianceException requiresDashboardSession() {
        return new PaymentComplianceException(403, "requires dashboard session");
    }
}
