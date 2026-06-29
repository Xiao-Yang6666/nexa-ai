package com.nexa.domain.billing.vo;

import com.nexa.domain.billing.exception.InvalidBillingParameterException;

/**
 * 兑换码状态值对象（DATA-MODEL §6 Redemption.Status 枚举）。
 *
 * <p>编码：{@code 1=未使用 / 2=已使用 / 3=已禁用}（prd-billing BL-4 §5「Status 1=未使用，
 * 已使用则拒绝」；DB-SCHEMA §6「1=未使用 / 已使用 / 已禁用」）。仅 {@link #UNUSED} 可被兑换，
 * {@link #USED}/{@link #DISABLED} 均拒绝。</p>
 */
public enum RedemptionStatus {

    /** 未使用（可兑换）。 */
    UNUSED(1),

    /** 已使用（一次性，兑换后置此态，不可重复兑换）。 */
    USED(2),

    /** 已禁用（管理员停用，拒绝兑换）。 */
    DISABLED(3);

    private final int code;

    RedemptionStatus(int code) {
        this.code = code;
    }

    /** @return 落库编码（DATA-MODEL §6） */
    public int code() {
        return code;
    }

    /** @return 是否可被兑换（仅 UNUSED 可兑换） */
    public boolean isRedeemable() {
        return this == UNUSED;
    }

    /**
     * 由编码重建状态（持久化重建方向）。
     *
     * @param code 落库编码
     * @return 对应状态
     * @throws InvalidBillingParameterException 编码未知时（脏数据信号，不静默吞）
     */
    public static RedemptionStatus fromCode(int code) {
        for (RedemptionStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new InvalidBillingParameterException("unknown redemption status code: " + code);
    }
}
