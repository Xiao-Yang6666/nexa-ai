package com.nexa.application.billing.port;

import java.util.Map;

/**
 * 支付渠道端口（应用层定义，基础设施层实现）。
 *
 * <p>抽象支付渠道（epay/stripe/creem/...）的两个交互（prd-billing BL-1）：
 * <ul>
 *   <li><b>下单跳转</b>：创建支付会话，返回收银台 URL + 跳转参数（pay_jump）；</li>
 *   <li><b>回调验签</b>：校验渠道异步回调的签名，伪造回调直接拒（pay_sign-否 → 丢弃不入账）。</li>
 * </ul>
 * 应用层只依赖本接口，具体渠道适配在 infra；可桩替换单测。</p>
 */
public interface PaymentGateway {

    /**
     * 为充值订单创建支付会话（生成收银台 URL + 跳转参数，BL-1 pay_jump）。
     *
     * @param tradeNo  商户订单号
     * @param provider 支付渠道标识
     * @param params   下单上下文（金额/币种/回调地址等，渠道相关）
     * @return 收银台跳转信息（含 payUrl + payParams）
     */
    PaymentSession createSession(String tradeNo, String provider, Map<String, Object> params);

    /**
     * 校验回调签名（BL-1 pay_sign：伪造回调验签失败直接丢弃，不入账）。
     *
     * @param provider 支付渠道标识（路径参数 {provider}）
     * @param payload  渠道回调原始体（含 trade_no + 签名）
     * @return 验签通过返回 {@code true}；伪造/篡改返回 {@code false}（调用方据此丢弃）
     */
    boolean verifyCallback(String provider, Map<String, Object> payload);

    /**
     * 支付会话信息（下单返回的收银台跳转载体）。
     *
     * @param payUrl    收银台 URL
     * @param payParams 跳转/提交参数（POST 表单字段等，可空）
     */
    record PaymentSession(String payUrl, Map<String, Object> payParams) {
    }
}
