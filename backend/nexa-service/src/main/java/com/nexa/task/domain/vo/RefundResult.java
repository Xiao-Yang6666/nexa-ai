package com.nexa.task.domain.vo;

/**
 * 退款/差额结算结果值对象（PRD AT-4 F-2009 产出，不可变）。
 *
 * <p>领域规则：按次计费跳过差额结算（{@link #SKIP}）；失败退预扣全额（{@link #fullRefund}）；
 * 成功按实际 token 重算差额（多退少不补，{@link #differential}）。</p>
 *
 * @param type        退款类型
 * @param refundQuota 退款配额（整数额度单位，0=无退款）
 */
public record RefundResult(Type type, long refundQuota) {

    /** 退款类型枚举。 */
    public enum Type {
        /** 跳过差额结算（按次计费）。 */
        SKIP,
        /** 全额退款（失败/超时）。 */
        FULL_REFUND,
        /** 差额结算（成功，多退少不补）。 */
        DIFFERENTIAL
    }

    /** 按次计费跳过（F-2009 PerCallBilling=true 分支）。 */
    public static final RefundResult SKIP = new RefundResult(Type.SKIP, 0);

    /**
     * 全额退款（失败/超时，退回预扣全额）。
     *
     * @param preConsumedQuota 预扣配额
     * @return 全额退款结果
     */
    public static RefundResult fullRefund(long preConsumedQuota) {
        return new RefundResult(Type.FULL_REFUND, preConsumedQuota);
    }

    /**
     * 差额结算（成功，多退少不补，PRD AT-4 §重算实际额度）。
     *
     * @param preConsumedQuota 预扣配额
     * @param actualQuota      实际消耗配额（按真实 token 重算）
     * @return 差额结算结果（多退差额，少不补=0退款）
     */
    public static RefundResult differential(long preConsumedQuota, long actualQuota) {
        long diff = preConsumedQuota - actualQuota;
        // 多退（diff>0），少不补（diff≤0 退0）。
        long refund = Math.max(0, diff);
        return new RefundResult(Type.DIFFERENTIAL, refund);
    }
}
