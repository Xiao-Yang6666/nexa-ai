package com.nexa.billing.interfaces.api.dto;

import java.util.Map;

/**
 * 充值下单结果客户视图（接口层出参，对齐 openapi {@code TopUpUserView}）。
 *
 * <p>SNAKE_CASE 全局策略下字段序列化为 {@code trade_no/pay_url/pay_params/status}。
 * 仅下发客户可见的收银台跳转信息，不含内部成本/渠道字段（零泄露）。</p>
 *
 * @param tradeNo   商户订单号
 * @param payUrl    收银台跳转 URL
 * @param payParams 跳转/提交参数（可空）
 * @param status    订单状态（恒为 pending）
 */
public record TopUpUserView(String tradeNo, String payUrl, Map<String, Object> payParams, String status) {
}
