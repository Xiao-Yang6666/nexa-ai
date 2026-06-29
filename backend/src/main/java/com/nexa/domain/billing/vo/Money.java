package com.nexa.domain.billing.vo;

import com.nexa.domain.billing.exception.InvalidBillingParameterException;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 金额值对象 —— 真实货币支付金额（充值/订阅付费）。
 *
 * <p>不可变、按值相等、零框架依赖。承载 DATA-MODEL §7 TopUp.Money、§8 SubscriptionPlan.PriceAmount、
 * §8.2 SubscriptionOrder.Money 等「支付金额」字段。底层用 {@link BigDecimal}（DB-SCHEMA 明确
 * 金额禁裸 float/double，用 numeric/decimal 精度安全，PG 注意条目反复强调）。</p>
 *
 * <p>区别于 {@link Quota}：Money 是「客户付的真实货币」（USD 等），Quota 是「平台内部配额单位」；
 * 充值是 Money→Quota 的入账（prd-billing BL-1：money 是支付金额、amount 是入账配额，两者独立）。</p>
 *
 * <p>不变量：金额非负（支付金额、套餐定价均 &gt;= 0）。统一标度到 6 位小数（对齐 DB
 * {@code decimal(*,6)} / {@code numeric}），避免不同标度的 BigDecimal 按值相等时误判。</p>
 */
public final class Money {

    /** 金额标度（小数位），对齐 DB-SCHEMA §7/§8 decimal scale=6。 */
    public static final int SCALE = 6;

    /** 零金额常量（余额支付 balance / 免费套餐）。 */
    public static final Money ZERO = new Money(BigDecimal.ZERO.setScale(SCALE));

    private final BigDecimal value;

    private Money(BigDecimal value) {
        this.value = value;
    }

    /**
     * 工厂方法：校验并构造金额值对象（从 BigDecimal）。
     *
     * @param raw 原始金额（须非空、&gt;= 0）
     * @return 标度归一（6 位小数）后的金额值对象
     * @throws InvalidBillingParameterException 当金额为空或为负时
     */
    public static Money of(BigDecimal raw) {
        if (raw == null) {
            throw new InvalidBillingParameterException("money must not be null");
        }
        if (raw.signum() < 0) {
            throw new InvalidBillingParameterException("money must be >= 0, got " + raw.toPlainString());
        }
        // 统一标度，保证 equals/hashCode 按值相等（0.5 与 0.500000 视为相等）。
        return new Money(raw.setScale(SCALE, java.math.RoundingMode.HALF_UP));
    }

    /**
     * 工厂方法：从 {@code double} 构造（接口层 openapi {@code money: number/double} 入参）。
     *
     * <p>用 {@code BigDecimal.valueOf} 而非 {@code new BigDecimal(double)}，避免二进制浮点误差
     * （后者会把 0.1 变成 0.1000000000000000055...）。</p>
     *
     * @param raw 原始金额（double）
     * @return 金额值对象
     * @throws InvalidBillingParameterException 当金额为负时
     */
    public static Money of(double raw) {
        return of(BigDecimal.valueOf(raw));
    }

    /** @return 金额（BigDecimal，标度 6） */
    public BigDecimal value() {
        return value;
    }

    /** @return 金额是否为零（余额支付/免费套餐判定） */
    public boolean isZero() {
        return value.signum() == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money other)) {
            return false;
        }
        return value.compareTo(other.value) == 0;
    }

    @Override
    public int hashCode() {
        // 用 stripTrailingZeros 保证不同标度但等值的金额 hashCode 一致（与 compareTo 语义对齐）。
        return Objects.hashCode(value.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
