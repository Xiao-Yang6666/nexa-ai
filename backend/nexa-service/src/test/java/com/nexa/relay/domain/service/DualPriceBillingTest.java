package com.nexa.relay.domain.service;

import com.nexa.relay.domain.ir.UsageIR;
import com.nexa.relay.domain.vo.BillingResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DualPriceBilling 单元测试（RL-7 第⑥⑦⑨步双价记账：售价/成本/利润）。
 */
class DualPriceBillingTest {

    @Test
    void sellUsesBasePriceAndGroupRatio() {
        // quota_sell = BasePriceRatio(A) × GroupRatio × UserDiscount × tokens
        UsageIR usage = UsageIR.of(100, 50);  // weightedTokens=150 (completionRatio=1)
        BillingResult r = DualPriceBilling.compute(usage,
                BigDecimal.valueOf(2),    // basePriceRatio
                BigDecimal.valueOf(0.8),  // groupRatio (vip 折扣)
                BigDecimal.ONE,           // userDiscount (不打折)
                BigDecimal.ONE,           // accountRatio
                BigDecimal.ONE);          // completionRatio
        // sell = 2 × 0.8 × 1 × 150 = 240
        assertEquals(240, r.quotaSell());
        // cost = basePrice(2) × accountRatio(1) × 150 = 300
        assertEquals(300, r.quotaCost());
        // profit = 240 - 300 = -60
        assertEquals(-60, r.quotaProfit());
        assertFalse(r.costMissing());
    }

    @Test
    void userDiscountMultipliesAfterGroupRatio() {
        // 售价三层相乘：base × group × userDiscount；用户折扣在分组折扣之后再乘。
        UsageIR usage = UsageIR.of(100, 0);  // weightedTokens=100
        BillingResult r = DualPriceBilling.compute(usage,
                BigDecimal.valueOf(2),    // basePriceRatio
                BigDecimal.valueOf(0.8),  // groupRatio (vip 档)
                BigDecimal.valueOf(0.5),  // userDiscount (大客户再 5 折)
                BigDecimal.ONE,           // accountRatio
                BigDecimal.ONE);          // completionRatio
        // sell = 2 × 0.8 × 0.5 × 100 = 80
        assertEquals(80, r.quotaSell());
        // 成本不含用户折扣：cost = 2 × 1 × 100 = 200
        assertEquals(200, r.quotaCost());
    }

    @Test
    void nullUserDiscountFallsBackToOne() {
        // userDiscount=null → 视为 1.0（不打折）
        UsageIR usage = UsageIR.of(100, 0);
        BillingResult r = DualPriceBilling.compute(usage,
                BigDecimal.valueOf(2), BigDecimal.ONE, null, BigDecimal.ONE, BigDecimal.ONE);
        assertEquals(200, r.quotaSell());
    }

    @Test
    void costUsesBasePriceAndAccountRatio() {
        // 成本 = BasePriceRatio(A) × AccountRatio × tokens；账号倍率压低成本则盈利。
        UsageIR usage = UsageIR.of(100, 0);  // weightedTokens=100
        BillingResult r = DualPriceBilling.compute(usage,
                BigDecimal.valueOf(2),    // basePriceRatio
                BigDecimal.ONE,           // groupRatio
                BigDecimal.ONE,           // userDiscount
                BigDecimal.valueOf(0.5),  // accountRatio (成本为售价基准一半)
                BigDecimal.ONE);          // completionRatio
        // sell = 2 × 1 × 100 = 200
        assertEquals(200, r.quotaSell());
        // cost = 2 × 0.5 × 100 = 100
        assertEquals(100, r.quotaCost());
        assertEquals(100, r.quotaProfit());
        assertFalse(r.costMissing());
    }

    @Test
    void lossWhenAccountRatioExceedsGroupDiscount() {
        // 账号倍率高于分组折扣 → quota_profit < 0（亏损告警态）
        UsageIR usage = UsageIR.of(100, 0);
        BillingResult r = DualPriceBilling.compute(usage,
                BigDecimal.valueOf(1),   // base
                BigDecimal.valueOf(0.5), // group 折扣压低售价
                BigDecimal.ONE,          // userDiscount
                BigDecimal.valueOf(2),   // 账号成本倍率高
                BigDecimal.ONE);         // completionRatio
        // sell = 1×0.5×100=50, cost=1×2×100=200, profit=-150
        assertEquals(50, r.quotaSell());
        assertEquals(200, r.quotaCost());
        assertEquals(-150, r.quotaProfit());
        assertTrue(r.isLoss());
    }

    @Test
    void completionRatioWeightsOutputTokens() {
        // completion token 更贵：weightedTokens = prompt + completion×ratio
        UsageIR usage = UsageIR.of(100, 100);
        BillingResult r = DualPriceBilling.compute(usage,
                BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE,          // userDiscount
                BigDecimal.ONE,          // accountRatio
                BigDecimal.valueOf(3));  // completion 3倍贵
        // weighted = 100 + 100×3 = 400
        assertEquals(400, r.quotaSell());
    }
}
