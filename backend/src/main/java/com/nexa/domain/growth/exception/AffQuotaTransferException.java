package com.nexa.domain.growth.exception;

import com.nexa.shared.kernel.HttpAwareDomainException;

/**
 * 邀请额度划转非法异常（PRD GR-5：低于最小单位 / 邀请额度不足 / 合规校验未过）。
 *
 * <p>对应 FL-growth GR-5 的三类拒绝态：
 * <ul>
 *   <li>低于最小划转单位拒绝（{@code quota < QuotaPerUnit}）</li>
 *   <li>邀请额度不足拒绝（{@code AffQuota < quota}）</li>
 *   <li>合规校验未过拒绝（{@code payment_compliance} 不通过）</li>
 * </ul>
 * 由 {@code AffiliateAccount} 聚合在划转行为内守护不变量并抛出（充血校验）。映射 HTTP 400。</p>
 */
public class AffQuotaTransferException extends HttpAwareDomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "AFF_TRANSFER_INVALID";

    /**
     * @param message 具体拒绝原因（用户可见）
     */
    public AffQuotaTransferException(String message) {
        super(CODE, 400, message);
    }
}
