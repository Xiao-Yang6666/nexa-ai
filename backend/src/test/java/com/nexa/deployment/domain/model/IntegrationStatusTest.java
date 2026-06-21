package com.nexa.deployment.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link IntegrationStatus} / {@link ConnectionTestResult} 派生计算单测（纯 JUnit，F-3039/F-3040）。
 *
 * <p>覆盖集成状态聚合的 {@code can_connect} 派生不变量（backend-engineer §2.2）：
 * can_connect = enabled &amp;&amp; configured 的真值表全覆盖；连接测试结果从硬件目录投影的口径。</p>
 */
@DisplayName("io.net 集成状态与连接测试派生")
class IntegrationStatusTest {

    @Test
    @DisplayName("can_connect：仅 enabled 且 configured 同时为真时为真（真值表）")
    void canConnectTruthTable() {
        assertTrue(IntegrationStatus.ionet(true, true).canConnect(), "启用且已配置 → 可连接");
        assertFalse(IntegrationStatus.ionet(true, false).canConnect(), "启用但未配置 key → 不可连接");
        assertFalse(IntegrationStatus.ionet(false, true).canConnect(), "未启用即便已配置 → 不可连接");
        assertFalse(IntegrationStatus.ionet(false, false).canConnect(), "均否 → 不可连接");
    }

    @Test
    @DisplayName("provider 固定 io.net")
    void providerFixed() {
        IntegrationStatus status = IntegrationStatus.ionet(true, true);
        assertEquals("io.net", status.provider());
        assertTrue(status.enabled());
        assertTrue(status.configured());
    }

    @Test
    @DisplayName("ConnectionTestResult.from：从硬件目录投影 hardware_count/total_available")
    void connectionTestFromCatalog() {
        HardwareCatalog catalog = new HardwareCatalog(java.util.List.of(
                new HardwareType(1, "A100", 8, java.util.Map.of()),
                new HardwareType(2, "H100", 12, java.util.Map.of())));
        ConnectionTestResult result = ConnectionTestResult.from(catalog);
        assertEquals(2, result.hardwareCount(), "硬件类型数");
        assertEquals(20L, result.totalAvailable(), "总可用副本数 = 8 + 12");
    }

    @Test
    @DisplayName("ConnectionTestResult.from：空目录 → 0/0")
    void connectionTestEmpty() {
        ConnectionTestResult result = ConnectionTestResult.from(new HardwareCatalog(java.util.List.of()));
        assertEquals(0, result.hardwareCount());
        assertEquals(0L, result.totalAvailable());
    }
}
