package com.nexa.infrastructure.growth.compliance;

import com.nexa.application.growth.port.PaymentComplianceCheck;
import org.springframework.stereotype.Component;

/**
 * 支付合规校验端口 {@link PaymentComplianceCheck} 的基线实现（基础设施层，PRD GR-5 T5）。
 *
 * <p>本切片提供<b>默认放行</b>的合规校验基线（仅校验金额非负这一最低门槛），保证编译与契约完整、
 * 划转主流程可端到端跑通。真实 {@code payment_compliance} 策略（风控黑名单、地区/资质限制、反洗钱
 * 规则等）接入时<b>替换本实现</b>即可——端口契约 {@link PaymentComplianceCheck} 不变，用例与领域零改动
 * （backend-engineer §2.3 依赖倒置，策略可演进而不动内层）。</p>
 *
 * <p>命名带 {@code Permissive} 明示其为放行基线，避免误以为已接入真实合规；替换实现时移除本类或调低
 * Bean 优先级即可。</p>
 */
@Component
public class PermissivePaymentComplianceAdapter implements PaymentComplianceCheck {

    /** {@inheritDoc} */
    @Override
    public boolean isCompliant(long userId, long amount) {
        // 基线：仅拒绝非正金额（其余一律放行）。真实合规策略接入后替换本实现。
        return amount > 0;
    }
}
