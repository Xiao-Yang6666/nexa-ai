package com.nexa.interfaces.billing.api.dto;

/**
 * 用户兑换请求（客户端入参，对齐 openapi {@code POST /api/user/topup} body {@code {key}}）。
 *
 * @param key 兑换码明文（必填）
 */
public record RedeemRequest(String key) {
}
