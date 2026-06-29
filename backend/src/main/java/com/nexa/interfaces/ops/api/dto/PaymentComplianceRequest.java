package com.nexa.interfaces.ops.api.dto;

/**
 * 支付合规确认请求（接口层入参 DTO，F-4030 POST /api/option/payment_compliance）。
 *
 * <p>对齐 API-ENDPOINTS §9.5 入参 {@code { confirmed }}（须为 true）。会话上下文校验
 * （仅 dashboard 会话）由 controller 据请求凭据来源判定，确认标志校验在领域。</p>
 *
 * @param confirmed 客户端确认标志
 */
public record PaymentComplianceRequest(boolean confirmed) {
}
