package com.nexa.domain.billing.service;

import com.nexa.domain.billing.vo.BillingResult;
import com.nexa.domain.billing.vo.Quota;
import com.nexa.domain.billing.vo.Ratio;
import com.nexa.domain.billing.vo.TokenUsage;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 基础倍率计费领域服务（无状态，纯业务，零框架依赖）。
 *
 * <p>实现 prd-billing 计费核心规则，跨多个值对象的计价逻辑放领域服务（backend-engineer §2.4
 * 领域服务）。覆盖：
 * <ul>
 *   <li><b>BL-6 基础倍率计费</b>（F-2038）：{@code quota = (prompt + completion×completion_ratio)
 *       × model_ratio × group_ratio}，含「非零倍率最小计费 1」与「免费模型 quota=0」守卫；</li>
 *   <li><b>BL-7 成本/售价分离</b>（F-2060/F-2061）：售价挂对外模型 A 恒定（{@code GetModelRatio(A)}），
 *       成本挂渠道×B（{@code cost_ratio}），各取各的、互不干扰；</li>
 *   <li><b>BL-8 分组折扣</b>（F-2062）：分组退化为纯折扣系数，最终扣费 = 基准价 × 折扣系数；</li>
 *   <li><b>BL-9 逐笔利润</b>（F-2063）：利润 = 售价 − 成本，成本<b>不乘</b>分组折扣（ADR-BILL-02）。</li>
 * </ul></p>
 *
 * <p>计价基准：token×倍率 的乘积是「等效基准 token 数」，除以 {@code QuotaPerUnit=500000}
 * 折算为 quota 单位（prd-billing BL-6 §5）。本服务用 {@link BigDecimal} 全程精确运算，最后
 * 向下取整为 long quota（与现网整数 quota 落账对齐）。</p>
 */
public final class BillingCalculator {

    /**
     * 计算售价额度（prd-billing BL-6 + BL-8，单一倍率口径）。
     *
     * <p>公式（BL-6 §3 步骤 4-6）：
     * <pre>
     *   等效输入 token = prompt + completion × completionRatio
     *   原始 quota     = 等效输入 × modelRatio × groupRatio ÷ QuotaPerUnit
     * </pre>
     * 其中 {@code modelRatio} 取对外模型 A 的基准售价倍率（BL-7 售价挂 A 恒定）、{@code groupRatio}
     * 取用户分组折扣系数（BL-8 free=1.0/vip=0.85/svip=0.7）。</p>
     *
     * <p>守卫（BL-6 §3 步骤 6 + §4）：
     * <ul>
     *   <li>合成倍率 {@code modelRatio × groupRatio == 0}（免费模型 / 分组倍率 0）→ {@code quota=0}
     *       （免费不扣费，prd-billing BL-6 免费模型态）；</li>
     *   <li>合成倍率 {@code != 0} 但算得 {@code quota <= 0}（极小 token）→ {@code quota=1}
     *       （非零倍率最小计费 1，AC「Log.Quota 落 1」）。</li>
     * </ul></p>
     *
     * @param usage          token 用量（结算用真实 token，预扣用估算 token）
     * @param modelRatio     模型倍率（售价侧取对外模型 A 的 BasePriceRatio）
     * @param groupRatio     分组折扣系数（BL-8 纯折扣，缺失由调用方回落 {@link Ratio#ONE}）
     * @param completionRatio 补全倍率（放大输出 token；纯输入场景传 {@link Ratio#ONE}）
     * @return 售价额度（quota 单位）
     */
    public Quota calculateSell(TokenUsage usage, Ratio modelRatio, Ratio groupRatio, Ratio completionRatio) {
        Ratio combined = modelRatio.multiply(groupRatio);
        // 免费模型 / 分组倍率为 0：合成倍率 0 → quota=0，不扣费（BL-6 免费模型态，不触发最小计费 1）。
        if (combined.isZero()) {
            return Quota.ZERO;
        }
        long raw = rawQuota(usage, combined, completionRatio);
        // 非零倍率最小计费 1：极小 token 算得 <=0 时兜底为 1（BL-6 §3 步骤 6 br_one）。
        if (raw <= 0) {
            return Quota.of(1L);
        }
        return Quota.of(raw);
    }

    /**
     * 计算成本额度（prd-billing BL-7 成本侧 + BL-9 ADR-BILL-02）。
     *
     * <p>公式：{@code cost = (prompt + completion × completionCostRatio) × costRatio ÷ QuotaPerUnit}。
     * 关键：成本<b>不乘分组折扣</b>（ADR-BILL-02：分组折扣是「卖给客户的让利」，与我方进货成本无关，
     * prd-billing BL-9 §1）。{@code costRatio} 取实际选中渠道×B 的成本倍率（BL-7 成本挂渠道×B）。</p>
     *
     * <p>成本不设「最小计费 1」（成本是真实进货，极小 token 成本就该接近 0；最小计费 1 是售价侧的
     * 商业兜底，不适用成本侧）。补全成本倍率为 0 时回落用 {@code costRatio}（DB-SCHEMA §22
     * {@code CompletionCostRatio=0} 回落 CostRatio×CompletionRatio，此处简化为回落 CostRatio）。</p>
     *
     * @param usage                token 用量（真实 token）
     * @param costRatio            成本倍率（渠道×B 的 cost_ratio）
     * @param completionCostRatio  补全成本倍率（0 时回落 costRatio）
     * @return 成本额度（quota 单位）
     */
    public Quota calculateCost(TokenUsage usage, Ratio costRatio, Ratio completionCostRatio) {
        if (costRatio.isZero()) {
            return Quota.ZERO;
        }
        // 补全成本倍率缺省（0）时回落用 costRatio（DB-SCHEMA §22 计费口径回落规则简化）。
        Ratio effCompletionCost = completionCostRatio.isZero() ? costRatio : completionCostRatio;
        long raw = rawCost(usage, costRatio, effCompletionCost);
        return Quota.of(Math.max(raw, 0L));
    }

    /**
     * 一次性结算售价+成本+利润（prd-billing BL-7/BL-8/BL-9 合并结算）。
     *
     * <p>结算阶段（链路第 18 步）算三个金额冗余落 Log（BL-9 §1）。成本侧 {@code cost} 由调用方
     * 在成本行缺失/禁用时传 {@code null}（BL-7 成本缺失态：quota_cost=0、利润虚高、标 cost_missing）。</p>
     *
     * @param sell 已算好的售价额度（含分组折扣）
     * @param cost 已算好的成本额度；{@code null} 表示成本行缺失（BL-7 兜底）
     * @return 含售价/成本/利润/缺失标记的结算结果
     */
    public BillingResult settle(Quota sell, Quota cost) {
        return BillingResult.of(sell, cost);
    }

    // ---- 内部精确运算（BigDecimal 全程，最后向下取整为 long quota） ----

    /**
     * 售价原始 quota：等效输入 token × 合成倍率 ÷ QuotaPerUnit（向下取整）。
     *
     * @param usage           token 用量
     * @param combined        合成倍率（model × group）
     * @param completionRatio 补全倍率
     * @return 向下取整后的原始 quota（可能 &lt;=0，由调用方兜底）
     */
    private static long rawQuota(TokenUsage usage, Ratio combined, Ratio completionRatio) {
        BigDecimal effectiveInput = effectiveInputTokens(usage, completionRatio);
        BigDecimal quota = effectiveInput
                .multiply(combined.value())
                .divide(BigDecimal.valueOf(Quota.QUOTA_PER_UNIT), 0, RoundingMode.FLOOR);
        return quota.longValueExact();
    }

    /**
     * 成本原始 quota：等效输入 token × costRatio ÷ QuotaPerUnit（向下取整）。
     *
     * @param usage               token 用量
     * @param costRatio           成本倍率
     * @param completionCostRatio 有效补全成本倍率
     * @return 向下取整后的成本 quota
     */
    private static long rawCost(TokenUsage usage, Ratio costRatio, Ratio completionCostRatio) {
        BigDecimal effectiveInput = effectiveInputTokens(usage, completionCostRatio);
        BigDecimal cost = effectiveInput
                .multiply(costRatio.value())
                .divide(BigDecimal.valueOf(Quota.QUOTA_PER_UNIT), 0, RoundingMode.FLOOR);
        return cost.longValueExact();
    }

    /**
     * 等效输入 token 数 = prompt + completion × completionRatio（prd-billing BL-6 §3 步骤 4）。
     *
     * @param usage           token 用量
     * @param completionRatio 补全倍率
     * @return 等效输入 token（BigDecimal，可能含小数）
     */
    private static BigDecimal effectiveInputTokens(TokenUsage usage, Ratio completionRatio) {
        BigDecimal prompt = BigDecimal.valueOf(usage.promptTokens());
        BigDecimal completionEffective = BigDecimal.valueOf(usage.completionTokens())
                .multiply(completionRatio.value());
        return prompt.add(completionEffective);
    }
}
