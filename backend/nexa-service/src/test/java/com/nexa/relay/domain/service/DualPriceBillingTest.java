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
        // quota_sell = BasePriceRatio(A) × GroupRatio × tokens
        UsageIR usage = UsageIR.of(100, 50);  // weightedTokens=150 (completionRatio=1)
        BillingResult r = DualPriceBilling.compute(usage,
                BigDecimal.valueOf(2),    // basePriceRatio
                BigDecimal.valueOf(0.8),  // groupRatio (vip 折扣)
                BigDecimal.valueOf(1),    // costRatio
                BigDecimal.ONE,           // accountRatio
                BigDecimal.ONE);          // completionRatio
        // sell = 2 × 0.8 × 150 = 240
        assertEquals(240, r.quotaSell());
        // cost = 1 × 150 = 150
        assertEquals(150, r.quotaCost());
        // profit = 240 - 150 = 90
        assertEquals(90, r.quotaProfit());
        assertFalse(r.costMissing());
    }

    @Test
    void costMissingFallsBackToZeroAndProfitEqualsSell() {
        // 成本行缺失：quota_cost=0、quota_profit=quota_sell
        UsageIR usage = UsageIR.of(100, 0);  // weightedTokens=100
        BillingResult r = DualPriceBilling.compute(usage,
                BigDecimal.ONE, BigDecimal.ONE, null, BigDecimal.ONE, BigDecimal.ONE);
        assertEquals(100, r.quotaSell());
        assertEquals(0, r.quotaCost());
        assertEquals(100, r.quotaProfit());
        assertTrue(r.costMissing());
    }

    @Test
    void lossWhenCostExceedsSell() {
        // quota_cost > quota_sell → quota_profit < 0（亏损告警态）
        UsageIR usage = UsageIR.of(100, 0);
        BillingResult r = DualPriceBilling.compute(usage,
                BigDecimal.valueOf(1),   // base
                BigDecimal.valueOf(0.5), // group 折扣压低售价
                BigDecimal.valueOf(2),   // cost 倍率高
                BigDecimal.ONE,          // accountRatio
                BigDecimal.ONE);         // completionRatio
        // sell = 1×0.5×100=50, cost=2×100=200, profit=-150
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
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE,          // accountRatio
                BigDecimal.valueOf(3));  // completion 3倍贵
        // weighted = 100 + 100×3 = 400
        assertEquals(400, r.quotaSell());
    }
}
