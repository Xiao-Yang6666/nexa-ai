package com.nexa.billing.infrastructure.payment;

import com.nexa.billing.application.port.PaymentGateway;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付渠道端口 {@link PaymentGateway} 的基线实现（基础设施层，prd-billing BL-1）。
 *
 * <p>本切片提供<b>占位/基线</b>的支付渠道适配：下单返回一个本地占位收银台 URL（按订单号拼接），
 * 回调验签为放行基线。真实渠道（epay/stripe/creem/...）接入时<b>替换本实现</b>即可——端口契约
 * {@link PaymentGateway} 不变，用例 {@code CreateTopUpOrderUseCase} 与领域零改动
 * （backend-engineer §2.3 依赖倒置，渠道可演进而不动内层）。</p>
 *
 * <p>命名带 {@code Baseline} 明示其为占位基线，避免误以为已接入真实支付；替换实现时移除本类或调低
 * Bean 优先级即可。</p>
 *
 * <p>可见性：本类不构造任何含成本/利润/上游模型的面向客户读路径，仅做渠道交互适配。</p>
 */
@Component
public class BaselinePaymentGatewayAdapter implements PaymentGateway {

    /** {@inheritDoc} */
    @Override
    public PaymentSession createSession(String tradeNo, String provider, Map<String, Object> params) {
        // 基线：返回本地占位收银台跳转信息（按订单号定位）。真实渠道接入后替换本实现。
        String payUrl = "/pay/checkout?trade_no=" + tradeNo
                + "&provider=" + (provider == null ? "" : provider);
        return new PaymentSession(payUrl, Map.of("trade_no", tradeNo));
    }

    /** {@inheritDoc} */
    @Override
    public boolean verifyCallback(String provider, Map<String, Object> payload) {
        // 基线：放行（真实验签逻辑随渠道接入替换）。伪造回调拦截属真实渠道职责。
        return payload != null && payload.get("trade_no") != null;
    }
}
