package com.nexa.interfaces.account.api.dto;

import com.nexa.domain.billing.model.BalanceTransaction;

import java.math.BigDecimal;

/**
 * 账变流水管理视图（接口层出参）。
 *
 * <p>amount/balanceAfter 由内部 quota 单位换算为 USD 展示（$1 = 500000 quota）。
 * type 为大写字面量（ADMIN_CREDIT/ADMIN_DEBIT/REDEEM/TOPUP）。</p>
 *
 * @param id           主键
 * @param type         账变类型字面量
 * @param amount       变动额（USD，带正负）
 * @param balanceAfter 变动后余额（USD）
 * @param operatorId   执行管理员 id（可空）
 * @param remark       备注（可空）
 * @param createdTime  创建时间 epoch 秒
 */
public record BalanceTransactionView(
        Long id,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Long operatorId,
        String remark,
        Long createdTime
) {
    /** $1 = 500000 quota（与前端 QUOTA_PER_USD 一致）。 */
    private static final BigDecimal QUOTA_PER_USD = BigDecimal.valueOf(500_000L);

    /**
     * 由领域账变记录裁剪为管理视图（quota → USD）。
     *
     * @param t 领域账变记录
     * @return 管理视图
     */
    public static BalanceTransactionView from(BalanceTransaction t) {
        return new BalanceTransactionView(
                t.id(),
                t.type().wireValue(),
                toUsd(t.amount()),
                toUsd(t.balanceAfter()),
                t.operatorId(),
                t.remark(),
                t.createdTime());
    }

    private static BigDecimal toUsd(long quota) {
        return BigDecimal.valueOf(quota).divide(QUOTA_PER_USD, 6, java.math.RoundingMode.HALF_UP);
    }
}
