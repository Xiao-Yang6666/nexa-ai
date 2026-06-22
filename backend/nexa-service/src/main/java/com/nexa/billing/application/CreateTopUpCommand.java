package com.nexa.billing.application;

/**
 * 充值下单命令（应用层入参，对齐 openapi {@code TopUpRequest}）。
 *
 * @param amount          充值额度（quota 单位）
 * @param money           支付金额（真实货币，double 入参）
 * @param paymentMethod   支付方式（stripe/creem/waffo/waffo_pancake/balance）
 * @param paymentProvider 支付渠道（epay/stripe/...）
 */
public record CreateTopUpCommand(Long amount, Double money, String paymentMethod, String paymentProvider) {
}
