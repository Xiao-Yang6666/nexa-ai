package com.nexa.application.ops.compliance;

import com.nexa.domain.ops.compliance.PaymentComplianceConfirmation;
import com.nexa.domain.ops.exception.PaymentComplianceException;
import com.nexa.domain.ops.option.Option;
import com.nexa.domain.ops.option.OptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付合规声明确认用例（应用层，F-4030 POST /api/option/payment_compliance）。
 *
 * <p>编排：领域护栏校验（{@link PaymentComplianceConfirmation#confirm}：dashboard 会话 + 已确认）
 * → 落 5 项 {@code payment_setting.compliance_*}（API-ENDPOINTS §9.5）。校验规则在领域（充血），
 * 用例只编排 + 守事务边界。状态变更：未确认→已确认（解锁支付/邀请正额度合规闸门）。</p>
 */
@Service
public class ConfirmPaymentComplianceUseCase {

    private static final String KEY_CONFIRMED = "payment_setting.compliance_confirmed";
    private static final String KEY_TERMS_VERSION = "payment_setting.compliance_terms_version";
    private static final String KEY_CONFIRMED_AT = "payment_setting.compliance_confirmed_at";
    private static final String KEY_CONFIRMED_BY = "payment_setting.compliance_confirmed_by";
    private static final String KEY_CONFIRMED_IP = "payment_setting.compliance_confirmed_ip";

    private final OptionRepository optionRepository;

    /**
     * @param optionRepository 选项仓储（写合规键，绕过 OptionRegistry 禁改护栏——本用例是合规键的唯一合法写入路径）
     */
    public ConfirmPaymentComplianceUseCase(OptionRepository optionRepository) {
        this.optionRepository = optionRepository;
    }

    /**
     * 确认支付合规声明。
     *
     * @param confirmed        客户端确认标志（须为 true）
     * @param dashboardSession 是否 dashboard 会话（access_token/API token 为 false）
     * @param confirmedBy      确认人用户名
     * @param confirmedIp      来源 IP
     * @return 合规确认结果（terms_version + confirmed_by）
     * @throws PaymentComplianceException 非 dashboard 会话（403）或未确认（400）
     */
    @Transactional
    public PaymentComplianceConfirmation execute(boolean confirmed,
                                                 boolean dashboardSession,
                                                 String confirmedBy,
                                                 String confirmedIp) {
        // 领域护栏：会话上下文 + 确认标志（不满足抛 403/400）。
        PaymentComplianceConfirmation confirmation =
                PaymentComplianceConfirmation.confirm(confirmed, dashboardSession, confirmedBy, confirmedIp);

        // 落 5 项合规键（覆盖式幂等：同版本重复确认覆盖）。
        optionRepository.save(Option.of(KEY_CONFIRMED, "true"));
        optionRepository.save(Option.of(KEY_TERMS_VERSION, confirmation.termsVersion()));
        optionRepository.save(Option.of(KEY_CONFIRMED_AT, Long.toString(confirmation.confirmedAt())));
        optionRepository.save(Option.of(KEY_CONFIRMED_BY, confirmation.confirmedBy()));
        optionRepository.save(Option.of(KEY_CONFIRMED_IP, confirmation.confirmedIp()));

        return confirmation;
    }
}
