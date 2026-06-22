package com.nexa.billing.domain.vo;

/**
 * 充值订单支付状态值对象（DATA-MODEL §7 TopUp.Status）。
 *
 * <p>状态机：{@link #PENDING}（下单待支付）→ {@link #SUCCESS}（回调验签通过入账）。
 * prd-billing BL-1 §3：下单创建 pending，首次有效回调置 success；放弃/超时保持 pending 可重发。
 * 幂等核心：已 SUCCESS 的订单重复回调不再变更（BL-1 pay_idem-是）。</p>
 */
public enum PaymentStatus {

    /** 待支付（下单创建，跳收银台前）。 */
    PENDING("pending"),

    /** 已支付成功（回调验签通过、额度已入账）。 */
    SUCCESS("success");

    private final String code;

    PaymentStatus(String code) {
        this.code = code;
    }

    /** @return 落库字符串编码（DATA-MODEL §7 status pending/success） */
    public String code() {
        return code;
    }

    /** @return 是否为已支付成功态（幂等判定：已 success 的回调不重复入账） */
    public boolean isPaid() {
        return this == SUCCESS;
    }

    /**
     * 由字符串编码重建状态（持久化重建方向）。
     *
     * <p>未知/历史编码（如 'paid'/'failed'）保守回落 PENDING（不静默当成 success，避免误入账）。</p>
     *
     * @param code 落库字符串
     * @return 对应状态（未知回落 PENDING）
     */
    public static PaymentStatus fromCode(String code) {
        if (SUCCESS.code.equalsIgnoreCase(code)) {
            return SUCCESS;
        }
        return PENDING;
    }
}
