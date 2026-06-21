package com.nexa.deployment.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 部署聚合派生计算单测（纯 JUnit，F-3049/F-3050/F-3054）。
 *
 * <p>覆盖充血聚合的派生不变量（backend-engineer §2.2）：硬件目录的 total/total_available 求和、
 * 地域目录的 total 上游 0 兜底、容器列表的 total 派生。按正常/边界组织。</p>
 */
@DisplayName("部署结果聚合派生计算")
class DeploymentCatalogTest {

    @Test
    @DisplayName("HardwareCatalog：total=类型数，total_available=各类型可用副本之和")
    void hardwareTotals() {
        HardwareCatalog catalog = new HardwareCatalog(List.of(
                new HardwareType(1, "A100", 10, Map.of("name", "A100")),
                new HardwareType(2, "H100", 25, Map.of("name", "H100")),
                new HardwareType(3, "L40", 0, Map.of("name", "L40"))));
        assertEquals(3, catalog.total());
        assertEquals(35L, catalog.totalAvailable());
    }

    @Test
    @DisplayName("HardwareCatalog：空目录 → total=0, total_available=0")
    void hardwareEmpty() {
        HardwareCatalog catalog = new HardwareCatalog(List.of());
        assertEquals(0, catalog.total());
        assertEquals(0L, catalog.totalAvailable());
    }

    @Test
    @DisplayName("LocationCatalog：上游 total>0 用上游值")
    void locationUpstreamTotal() {
        LocationCatalog catalog = new LocationCatalog(List.of(
                new Location("us", "US", Map.of()),
                new Location("eu", "EU", Map.of())), 9);
        assertEquals(9L, catalog.total(), "上游声明 9 应优先");
    }

    @Test
    @DisplayName("LocationCatalog：上游 total=0 → 回退列表长度（F-3050 兜底）")
    void locationFallbackTotal() {
        LocationCatalog catalog = new LocationCatalog(List.of(
                new Location("us", "US", Map.of()),
                new Location("eu", "EU", Map.of()),
                new Location("ap", "APAC", Map.of())), 0);
        assertEquals(3L, catalog.total(), "上游 0 时用列表长度兜底");
    }

    @Test
    @DisplayName("ContainerList：total=容器数；无容器→空数组 + total=0")
    void containerTotal() {
        ContainerList list = new ContainerList(List.of(
                new ContainerSummary("c1", "d1", 99.5, "https://x", List.of(
                        new ContainerEvent("t1", "started"))),
                new ContainerSummary("c2", "d2", 80.0, null, List.of())));
        assertEquals(2, list.total());

        ContainerList empty = new ContainerList(null);
        assertEquals(0, empty.total());
        assertEquals(0, empty.containers().size());
    }
}
