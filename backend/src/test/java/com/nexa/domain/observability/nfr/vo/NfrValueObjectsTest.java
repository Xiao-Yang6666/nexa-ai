package com.nexa.domain.observability.nfr.vo;

import com.nexa.domain.observability.nfr.service.ChannelHealthPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NFR/Scalability 值对象与领域服务单测（纯 JUnit）——F-5001~5005 / F-5013~5015。
 *
 * <p>覆盖延迟预算 / 压测门禁 / 可用性 / 容灾 / 缓存命中率 / 渠道故障切换的达标判定。</p>
 */
@DisplayName("NFR 横切值对象")
class NfrValueObjectsTest {

    @Test
    @DisplayName("LatencyBudget：默认 15/60ms，达标判定双条件")
    void latencyBudget() {
        LatencyBudget b = LatencyBudget.contractDefault();
        assertTrue(b.isMet(10, 50), "p50<=15 且 p99<=60 达标");
        assertFalse(b.isMet(20, 50), "p50 超标不达标");
        assertFalse(b.isMet(10, 70), "p99 超标不达标");
        assertThrows(IllegalArgumentException.class, () -> new LatencyBudget(60, 15), "p99<p50 非法");
    }

    @Test
    @DisplayName("ThroughputBenchmark：1500rps/5000并发/0.1% 门禁三条件")
    void throughputBenchmark() {
        ThroughputBenchmark t = ThroughputBenchmark.contractDefault();
        assertTrue(t.passes(1600, 5200, 0.0005));
        assertFalse(t.passes(1400, 5200, 0.0005), "吞吐不足阻断");
        assertFalse(t.passes(1600, 4000, 0.0005), "并发不足阻断");
        assertFalse(t.passes(1600, 5200, 0.002), "错误率超标阻断");
    }

    @Test
    @DisplayName("AvailabilityTarget：数据面99.9%/控制面99.5%，停机预算换算")
    void availabilityTarget() {
        AvailabilityTarget a = AvailabilityTarget.contractDefault();
        assertTrue(a.isDataPlaneMet(0.9995));
        assertFalse(a.isDataPlaneMet(0.998));
        assertTrue(a.isControlPlaneMet(0.996));
        // 99.9% → 每月约 43 分钟停机预算（30d）。
        assertTrue(a.dataPlaneMonthlyDowntimeBudgetMinutes() > 0);
    }

    @Test
    @DisplayName("BackupRecoveryObjective：RTO<=30min/RPO<=5min 达标判定")
    void backupRecovery() {
        BackupRecoveryObjective o = BackupRecoveryObjective.contractDefault();
        assertTrue(o.isMet(25, 4));
        assertFalse(o.isMet(35, 4), "RTO 超标");
        assertFalse(o.isMet(25, 6), "RPO 超标");
    }

    @Test
    @DisplayName("CacheHitStats：命中率>=95% 且回源<5% 健康")
    void cacheHitStats() {
        assertTrue(new CacheHitStats(960, 40).isHealthy(), "96% 命中健康");
        assertFalse(new CacheHitStats(900, 100).isHealthy(), "90% 命中不健康");
        assertTrue(new CacheHitStats(0, 0).isHealthy(), "无访问视为健康(命中率1.0)");
        assertThrows(IllegalArgumentException.class, () -> new CacheHitStats(-1, 0));
    }

    @Test
    @DisplayName("LogArchivalPolicy：月度分区键 + 超期归档判定")
    void logArchival() {
        LogArchivalPolicy p = LogArchivalPolicy.contractDefault();
        // 2026-06-21 → 分区 202606
        assertTrue(p.partitionKey(java.time.Instant.parse("2026-06-21T12:00:00Z").getEpochSecond())
                .equals("202606"));
        java.time.Instant recorded = java.time.Instant.parse("2026-01-01T00:00:00Z");
        assertTrue(p.shouldArchive(recorded, java.time.Instant.parse("2026-06-01T00:00:00Z")), "5个月前应归档");
        assertFalse(p.shouldArchive(recorded, java.time.Instant.parse("2026-02-01T00:00:00Z")), "31天内不归档");
    }

    @Test
    @DisplayName("ChannelHealthPolicy：最多 1 次故障切换、连续 3 次失败判不健康")
    void channelHealth() {
        assertTrue(ChannelHealthPolicy.canFailover(0), "首次故障可切换");
        assertFalse(ChannelHealthPolicy.canFailover(1), "已切换 1 次不再切换");
        assertTrue(ChannelHealthPolicy.shouldSwitchToHealthy(true, 0), "故障且未超预算应切换");
        assertFalse(ChannelHealthPolicy.shouldSwitchToHealthy(false, 0), "未故障不切换");
        assertFalse(ChannelHealthPolicy.shouldSwitchToHealthy(true, 1), "故障但已用尽预算不切换");
        assertTrue(ChannelHealthPolicy.isUnhealthy(3));
        assertFalse(ChannelHealthPolicy.isUnhealthy(2));
    }
}
