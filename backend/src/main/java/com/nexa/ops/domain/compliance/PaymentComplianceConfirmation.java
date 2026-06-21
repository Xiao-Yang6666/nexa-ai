package com.nexa.ops.domain.compliance;

import com.nexa.ops.domain.exception.PaymentComplianceException;

import java.time.Instant;

/**
 * 支付合规声明确认（充血值对象/领域服务，F-4030 + Compliance F-5021）。
 *
 * <p>承载「root 在 dashboard 会话中确认支付/邀请合规声明」这一动作的领域规则与结果。确认后
 * 解锁支付、邀请正额度等合规闸门（状态变更：未确认→已确认）。确认须满足两条不变量
 * （构造期守护，充血 backend-engineer §2.2）：
 * <ol>
 *   <li>必须为 dashboard 会话（拒绝 access_token / API token）→ 否则 403；</li>
 *   <li>{@code confirmed} 必须为 true → 否则 400。</li>
 * </ol>
 * </p>
 *
 * <p>领域规则来源：API-ENDPOINTS §9.5 POST /api/option/payment_compliance。确认结果落 5 项
 * {@code payment_setting.compliance_*}（confirmed/terms_version/confirmed_at/confirmed_by/confirmed_ip）。</p>
 *
 * @param termsVersion 合规条款版本
 * @param confirmedBy  确认人（用户名，仅审计可读性）
 * @param confirmedIp  确认来源 IP（审计）
 * @param confirmedAt  确认时间 epoch 秒
 */
public record PaymentComplianceConfirmation(String termsVersion,
                                            String confirmedBy,
                                            String confirmedIp,
                                            long confirmedAt) {

    /** 当前合规条款版本（与现网约定一致，集中常量便于升级）。 */
    public static final String CURRENT_TERMS_VERSION = "v1";

    /**
     * 校验并产生一次合规确认（F-4030 全部领域护栏）。
     *
     * @param confirmed          客户端提交的确认标志（须为 true）
     * @param dashboardSession   当前请求是否为 dashboard 会话（access_token/API token 为 false）
     * @param confirmedBy        确认人用户名
     * @param confirmedIp        来源 IP
     * @return 合法的合规确认（terms_version = 当前版本，confirmed_at = 当前时间）
     * @throws PaymentComplianceException 非 dashboard 会话（403）或未确认（400）
     */
    public static PaymentComplianceConfirmation confirm(boolean confirmed,
                                                        boolean dashboardSession,
                                                        String confirmedBy,
                                                        String confirmedIp) {
        // 规则 1：会话上下文护栏先行（越权语义优先于入参校验，避免泄露「会话合法但未勾选」细节）。
        if (!dashboardSession) {
            throw PaymentComplianceException.requiresDashboardSession();
        }
        // 规则 2：必须显式确认。
        if (!confirmed) {
            throw PaymentComplianceException.notConfirmed();
        }
        return new PaymentComplianceConfirmation(
                CURRENT_TERMS_VERSION,
                confirmedBy,
                confirmedIp,
                Instant.now().getEpochSecond());
    }
}
