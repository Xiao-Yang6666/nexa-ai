package com.nexa.application.growth.port;

/**
 * 支付合规校验端口（应用层定义，基础设施层实现，PRD GR-5 T5 {@code payment_compliance}）。
 *
 * <p>邀请额度划转为可用额度前的合规闸（FL-growth GR-5 T5「通过 payment_compliance?」）。原系统由
 * {@code payment_compliance} 模块判定某用户/某笔划转是否满足风控/合规要求。增长域只依赖本端口的布尔
 * 判定，具体合规策略（黑名单、风控规则、地区限制等）由基础设施层实现承载，可随合规要求演进而不动
 * 领域/用例（backend-engineer §2.3）。</p>
 *
 * <p>本切片提供一个默认放行的基线实现（{@code PermissivePaymentComplianceAdapter}），保证编译与契约
 * 完整；真实合规策略接入后替换实现即可，端口契约不变。</p>
 */
public interface PaymentComplianceCheck {

    /**
     * 判定某用户的某笔邀请额度划转是否通过支付合规校验（GR-5 T5）。
     *
     * @param userId 划转发起用户 id
     * @param amount 本次划转额度
     * @return 通过返回 {@code true}；未过返回 {@code false}（用例据此拒绝划转）
     */
    boolean isCompliant(long userId, long amount);
}
