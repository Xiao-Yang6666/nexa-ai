package com.nexa.application.billing.result;

import com.nexa.domain.billing.vo.Money;
import com.nexa.domain.billing.vo.Quota;

/**
 * 创建充值下单结果（应用层出参，prd-billing BL-1 pay_order/pay_jump）。
 *
 * @param tradeNo   生成的商户订单号
 * @param payUrl    收银台跳转 URL
 * @param payParams 跳转/提交参数（JSON 对象，可空）
 * @param status    订单状态（恒为 pending）
 */
public record CreateTopUpResult(String tradeNo, String payUrl,
                                java.util.Map<String, Object> payParams, String status) {
}
