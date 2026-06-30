package com.nexa.interfaces.api.billing.dto;

/**
 * 充值下单请求（客户端入参，对齐 openapi {@code POST /api/topup} body {@code TopUpRequest}）。
 *
 * <p>字段经全局 Jackson {@code SNAKE_CASE} 反序列化（{@code payment_method}→{@code paymentMethod}、
 * {@code payment_provider}→{@code paymentProvider}）。</p>
 *
 * @param amount          充值额度（quota 单位）
 * @param money           支付金额（真实货币）
 * @param paymentMethod   支付方式（stripe/creem/waffo/waffo_pancake/balance）
 * @param paymentProvider 支付渠道（epay/stripe/...）
 */
public record TopUpRequest(Long amount, Double money, String paymentMethod, String paymentProvider) {
}
