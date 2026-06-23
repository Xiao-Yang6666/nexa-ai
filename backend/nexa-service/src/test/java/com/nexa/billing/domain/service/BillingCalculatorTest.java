package com.nexa.billing.domain.service;

import com.nexa.billing.domain.vo.BillingResult;
import com.nexa.billing.domain.vo.Quota;
import com.nexa.billing.domain.vo.Ratio;
import com.nexa.billing.domain.vo.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BillingCalculator} 基础倍率计费领域服务单测（纯 JUnit，不起 Spring/DB）。
 *
 * <p>逐条覆盖 prd-billing BL-6/BL-7/BL-8/BL-9 的验收标准（按正常/边界/异常三类组织，
 * backend-engineer §3.3）。计价基准 {@code QuotaPerUnit=500000}，为让小 token 的乘积可观测，
 * 多数用例用大 token 数（如 500000）放大到 quota 单位。</p>
 */
@DisplayName("BillingCalculator 基础倍率计费")
class BillingCalculatorTest {

    private final BillingCalculator calc = new BillingCalculator();

    @Nested
    @DisplayName("BL-6 基础倍率计费（售价）")
    class BasicRatio {

        @Test
        @DisplayName("纯输入：合成倍率 = model×group（completion_ratio=1）")
        void pureInputCombinedRatio() {
            // model=2, group=1.5, completion=1, prompt=500000 → 等效=500000
            // quota = 500000 × 2 × 1.5 ÷ 500000 = 3
            Quota q = calc.calculateSell(
                    new TokenUsage(500_000L, 0L), Ratio.of(2.0), Ratio.of(1.5), Ratio.ONE);
            assertEquals(3L, q.value());
        }

        @Test
        @DisplayName("含补全倍率：等效输入 = prompt + completion×completion_ratio")
        void completionRatioAmplifiesOutput() {
            // prompt=100×5000, completion=50×5000, completion_ratio=4 → 等效 = (100+50×4)×5000 = 300×5000
            // quota = 300×5000 × 2 × 1.5 ÷ 500000 = 2250000000/500000 = 4500
            Quota q = calc.calculateSell(
                    new TokenUsage(500_000L, 250_000L), Ratio.of(2.0), Ratio.of(1.5), Ratio.of(4.0));
            // 等效 = 500000 + 250000×4 = 1500000；quota = 1500000×3 ÷ 500000 = 9
            assertEquals(9L, q.value());
        }

        @Test
        @DisplayName("非零倍率但算得 quota<=0（极小 token）→ 最小计费 1")
        void nonZeroRatioMinChargeOne() {
            // prompt=1, model=2, group=1.5 → 1×3 ÷ 500000 = 0（向下取整）→ 兜底 1
            Quota q = calc.calculateSell(
                    new TokenUsage(1L, 0L), Ratio.of(2.0), Ratio.of(1.5), Ratio.ONE);
            assertEquals(1L, q.value());
        }

        @Test
        @DisplayName("合成倍率为 0（免费模型）→ quota=0，不触发最小计费")
        void freeModelZeroQuota() {
            Quota q1 = calc.calculateSell(
                    new TokenUsage(500_000L, 0L), Ratio.ZERO, Ratio.of(1.5), Ratio.ONE);
            Quota q2 = calc.calculateSell(
                    new TokenUsage(500_000L, 0L), Ratio.of(2.0), Ratio.ZERO, Ratio.ONE);
            assertTrue(q1.isZero());
            assertTrue(q2.isZero());
        }
    }

    @Nested
    @DisplayName("BL-8 分组折扣（free/vip/svip）")
    class GroupDiscount {

        @Test
        @DisplayName("free=1.0 不打折 = 基准价")
        void freeNoDiscount() {
            // base=4, group=1.0, prompt=500000 → 4×1 = 4
            Quota q = calc.calculateSell(
                    new TokenUsage(500_000L, 0L), Ratio.of(4.0), Ratio.ONE, Ratio.ONE);
            assertEquals(4L, q.value());
        }

        @Test
        @DisplayName("svip=0.7 → 基准价 × 0.7")
        void svipSeventyPercent() {
            // base=10, group=0.7, prompt=500000 → 10×0.7 = 7
            Quota q = calc.calculateSell(
                    new TokenUsage(500_000L, 0L), Ratio.of(10.0), Ratio.of(0.7), Ratio.ONE);
            assertEquals(7L, q.value());
        }
    }

    @Nested
    @DisplayName("BL-7 成本（不乘分组折扣）")
    class Cost {

        @Test
        @DisplayName("成本 = tokens × costRatio ÷ QuotaPerUnit，与分组无关")
        void costIndependentOfGroup() {
            // cost_ratio=1, prompt=500000 → cost = 500000×1 ÷ 500000 = 1
            Quota cost = calc.calculateCost(
                    new TokenUsage(500_000L, 0L), Ratio.of(1.0), Ratio.ZERO);
            assertEquals(1L, cost.value());
        }

        @Test
        @DisplayName("成本倍率为 0 → 成本 0")
        void zeroCostRatio() {
            Quota cost = calc.calculateCost(
                    new TokenUsage(500_000L, 0L), Ratio.ZERO, Ratio.ZERO);
            assertTrue(cost.isZero());
        }
    }

    @Nested
    @DisplayName("BL-9 逐笔利润 = 售价 − 成本")
    class Profit {

        @Test
        @DisplayName("利润 = sell − cost，正常正利润")
        void positiveProfit() {
            BillingResult r = calc.settle(Quota.of(10L), Quota.of(4L));
            assertEquals(10L, r.quotaSell().value());
            assertEquals(4L, r.quotaCost().value());
            assertEquals(6L, r.quotaProfit());
            assertFalse(r.costMissing());
        }

        @Test
        @DisplayName("成本高于售价 → 利润为负（亏损告警信号）")
        void negativeProfit() {
            BillingResult r = calc.settle(Quota.of(3L), Quota.of(5L));
            assertEquals(-2L, r.quotaProfit());
        }

        @Test
        @DisplayName("成本缺失（null）→ quota_cost=0、利润=售价、标 cost_missing")
        void costMissingInflatesProfit() {
            BillingResult r = calc.settle(Quota.of(8L), null);
            assertEquals(0L, r.quotaCost().value());
            assertEquals(8L, r.quotaProfit());
            assertTrue(r.costMissing());
        }
    }
}
