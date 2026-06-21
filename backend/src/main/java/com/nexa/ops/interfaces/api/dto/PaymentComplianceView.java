package com.nexa.ops.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.ops.domain.compliance.PaymentComplianceConfirmation;

/**
 * 支付合规确认视图（接口层出参 DTO，F-4030 POST /api/option/payment_compliance）。
 *
 * <p>对齐 API-ENDPOINTS §9.5 出参 {@code { terms_version, confirmed_by }}。客户视图铁律：仅回显
 * 条款版本 + 确认人，不回显确认 IP（confirmed_ip 落库审计但不下发）。</p>
 *
 * @param termsVersion 合规条款版本
 * @param confirmedBy  确认人
 */
public record PaymentComplianceView(
        @JsonProperty("terms_version") String termsVersion,
        @JsonProperty("confirmed_by") String confirmedBy) {

    /**
     * 由领域确认结果裁剪为视图（剔除 confirmed_ip）。
     *
     * @param confirmation 领域合规确认
     * @return 视图
     */
    public static PaymentComplianceView from(PaymentComplianceConfirmation confirmation) {
        return new PaymentComplianceView(confirmation.termsVersion(), confirmation.confirmedBy());
    }
}
