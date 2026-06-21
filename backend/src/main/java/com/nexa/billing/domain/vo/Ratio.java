package com.nexa.billing.domain.vo;

import com.nexa.billing.domain.exception.InvalidBillingParameterException;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 计费倍率值对象 —— 模型倍率 / 分组折扣系数 / 补全倍率 / 成本倍率的统一承载。
 *
 * <p>不可变、按值相等、零框架依赖。承载 prd-billing BL-6/BL-7/BL-8 的所有倍率：
 * <ul>
 *   <li>{@code model_ratio}（模型倍率，决定相对基准 {@code 1 === $0.002/1K} 的单价）；</li>
 *   <li>{@code group_ratio}（分组折扣系数，BL-8 语义收窄为纯折扣 free=1.0/vip=0.85/svip=0.7）；</li>
 *   <li>{@code completion_ratio}（补全倍率，放大输出 token）；</li>
 *   <li>{@code cost_ratio}/{@code completion_cost_ratio}（BL-7 成本倍率，挂渠道×B）。</li>
 * </ul>
 * 底层 {@link BigDecimal}（倍率涉及小数与乘积，禁裸 double 避免精度漂移；DB-SCHEMA §19/§22
 * base_price_ratio/cost_ratio 用 numeric）。</p>
 *
 * <p>不变量：倍率非负（{@code ratio >= 0}）。{@code 0} 是合法值（免费模型 / 成本缺失兜底
 * {@code quota_cost=0}，prd-billing BL-6 免费模型态、BL-7 成本缺失态）。</p>
 */
public final class Ratio {

    /** 倍率 1（基准价 / 折扣不打折 / 分组缺失兜底，prd-billing BL-6 group ratio 兜底 1、BL-8 free=1.0）。 */
    public static final Ratio ONE = new Ratio(BigDecimal.ONE);

    /** 倍率 0（免费模型 / 成本缺失兜底 quota_cost=0）。 */
    public static final Ratio ZERO = new Ratio(BigDecimal.ZERO);

    private final BigDecimal value;

    private Ratio(BigDecimal value) {
        this.value = value;
    }

    /**
     * 工厂方法：校验并构造倍率值对象（从 BigDecimal）。
     *
     * @param raw 原始倍率（须非空、&gt;= 0）
     * @return 倍率值对象
     * @throws InvalidBillingParameterException 当倍率为空或为负时
     */
    public static Ratio of(BigDecimal raw) {
        if (raw == null) {
            throw new InvalidBillingParameterException("ratio must not be null");
        }
        if (raw.signum() < 0) {
            throw new InvalidBillingParameterException("ratio must be >= 0, got " + raw.toPlainString());
        }
        return new Ratio(raw);
    }

    /**
     * 工厂方法：从 {@code double} 构造（运行时 KV 配置反序列化常用）。
     *
     * @param raw 原始倍率（double）
     * @return 倍率值对象
     * @throws InvalidBillingParameterException 当倍率为负时
     */
    public static Ratio of(double raw) {
        return of(BigDecimal.valueOf(raw));
    }

    /** @return 倍率（BigDecimal） */
    public BigDecimal value() {
        return value;
    }

    /** @return 是否为零倍率（免费模型 / 成本缺失判定） */
    public boolean isZero() {
        return value.signum() == 0;
    }

    /**
     * 倍率相乘（合成倍率：{@code model_ratio × group_ratio × completion_ratio}）。
     *
     * @param other 另一倍率
     * @return 乘积倍率（新实例，不可变）
     */
    public Ratio multiply(Ratio other) {
        return new Ratio(this.value.multiply(other.value));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ratio other)) {
            return false;
        }
        return value.compareTo(other.value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
