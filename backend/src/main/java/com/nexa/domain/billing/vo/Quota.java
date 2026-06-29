package com.nexa.domain.billing.vo;

import com.nexa.domain.billing.exception.InvalidBillingParameterException;

import java.util.Objects;

/**
 * 额度（配额）值对象 —— 计费体系的内部货币单位。
 *
 * <p>不可变、按值相等、零框架依赖（DDD 战术完整档：金额/配额做值对象，避免裸 long 散落
 * 与精度问题，backend-engineer §2.4 / Pitfall 7）。承载 DATA-MODEL §1 User.Quota、§6
 * Redemption.Quota、§7 TopUp.Amount、§8 UserSubscription.AmountTotal/AmountUsed 等所有
 * 「配额整数额度」字段，底层为 {@code long}（对齐 DB bigint）。</p>
 *
 * <p>计价基准：{@code common.QuotaPerUnit = 500000}（{@code $0.002/1K tokens} → 1 quota），
 * 见 prd-billing BL-6 §5（本值对象只承载额度数值，倍率折算在 {@code BillingCalculator}）。</p>
 *
 * <p>不变量：额度非负（充值/兑换面额、订阅额度、用户余额均 &gt;= 0）。负额度是计费 bug 的信号，
 * 构造期即拒，不让脏值流入扣费/入账（不吞错）。</p>
 */
public final class Quota {

    /** 零额度常量（免费模型 / 初始余额）。 */
    public static final Quota ZERO = new Quota(0L);

    /**
     * 计价基准常量：{@code $0.002/1K tokens} 折算为 1 quota 单位（prd-billing BL-6 §5
     * {@code common.QuotaPerUnit}）。预扣/结算的 token×倍率 最终除以本常量得 quota。
     */
    public static final long QUOTA_PER_UNIT = 500_000L;

    private final long value;

    private Quota(long value) {
        this.value = value;
    }

    /**
     * 工厂方法：校验并构造额度值对象。
     *
     * @param value 额度数值（quota 单位，须 &gt;= 0）
     * @return 额度值对象
     * @throws InvalidBillingParameterException 当额度为负时
     */
    public static Quota of(long value) {
        if (value < 0) {
            throw new InvalidBillingParameterException("quota must be >= 0, got " + value);
        }
        return new Quota(value);
    }

    /** @return 额度数值（quota 单位） */
    public long value() {
        return value;
    }

    /**
     * 额度相加（充值/兑换入账：{@code user.Quota += amount}）。
     *
     * @param other 待累加的额度
     * @return 新额度值对象（不可变，返回新实例）
     */
    public Quota plus(Quota other) {
        return new Quota(this.value + other.value);
    }

    /**
     * 额度相减（扣费/结算）。
     *
     * <p>结果允许为 0，但不允许为负——上层在调用前应已校验余额充足（prd-billing BL-2
     * {@code userQuota - preConsumed < 0} 直接 403），此处再次守护，杜绝扣成负余额。</p>
     *
     * @param other 待扣减的额度
     * @return 新额度值对象
     * @throws InvalidBillingParameterException 当扣减后为负时
     */
    public Quota minus(Quota other) {
        long result = this.value - other.value;
        if (result < 0) {
            throw new InvalidBillingParameterException(
                    "quota underflow: " + this.value + " - " + other.value + " < 0");
        }
        return new Quota(result);
    }

    /** @return 额度是否为零（用于免费模型判定） */
    public boolean isZero() {
        return value == 0L;
    }

    /**
     * 是否足以覆盖给定额度（余额是否够扣）。
     *
     * @param needed 需要的额度
     * @return 本额度 &gt;= needed 返回 {@code true}
     */
    public boolean covers(Quota needed) {
        return this.value >= needed.value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Quota other)) {
            return false;
        }
        return value == other.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "Quota(" + value + ")";
    }
}
