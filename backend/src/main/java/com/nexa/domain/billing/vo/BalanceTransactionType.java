package com.nexa.domain.billing.vo;

import com.nexa.domain.billing.exception.InvalidBillingParameterException;

/**
 * 账变类型值对象（balance_transactions.type 落库字面量）。
 *
 * <p>标识一条余额账变记录的来源。本期只记<b>管理操作类 + 充值到账类</b>（不含 API 消费扣减——
 * 那是请求日志的范畴，量极大且已在 logs 表）：
 * <ul>
 *   <li>{@link #ADMIN_CREDIT} 管理员手动充值（后台给指定用户加额度）；</li>
 *   <li>{@link #ADMIN_DEBIT}  管理员手动扣费（后台扣减，扣到 0 为止）；</li>
 *   <li>{@link #REDEEM}       兑换码到账（预留，便于统一展示）；</li>
 *   <li>{@link #TOPUP}        自助充值到账（预留）。</li>
 * </ul>
 * 大写字面量落库，与 API enum 一致。</p>
 */
public enum BalanceTransactionType {

    /** 管理员手动充值。 */
    ADMIN_CREDIT("ADMIN_CREDIT"),

    /** 管理员手动扣费。 */
    ADMIN_DEBIT("ADMIN_DEBIT"),

    /** 兑换码到账。 */
    REDEEM("REDEEM"),

    /** 自助充值到账。 */
    TOPUP("TOPUP");

    private final String wireValue;

    BalanceTransactionType(String wireValue) {
        this.wireValue = wireValue;
    }

    /** @return 落库/线上字面量（大写） */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 由字面量解析类型（持久化重建方向）。
     *
     * @param raw 原始字面量
     * @return 对应类型
     * @throws InvalidBillingParameterException raw 为空或非法
     */
    public static BalanceTransactionType fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidBillingParameterException("balance transaction type is required");
        }
        String normalized = raw.trim().toUpperCase();
        for (BalanceTransactionType t : values()) {
            if (t.wireValue.equals(normalized)) {
                return t;
            }
        }
        throw new InvalidBillingParameterException("invalid balance transaction type: " + raw);
    }
}
