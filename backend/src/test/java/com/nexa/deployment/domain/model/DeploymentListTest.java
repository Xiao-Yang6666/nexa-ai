package com.nexa.deployment.domain.model;

import com.nexa.deployment.domain.vo.Pagination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DeploymentList} / {@link DeploymentSummary} 充血聚合单测（纯 JUnit，F-3041/F-3042）。
 *
 * <p>覆盖部署列表聚合的两条派生不变量（backend-engineer §2.2）：</p>
 * <ul>
 *   <li>{@link DeploymentList#statusCounts()}：all=总数 + 各 status 计数（含空 status 归 unknown 桶）。</li>
 *   <li>{@link DeploymentList#filterByKeyword(String)}：名称小写包含过滤 + total 修正；空关键词不过滤。</li>
 * </ul>
 * <p>按正常/边界/异常组织。</p>
 */
@DisplayName("部署列表聚合派生（状态计数 + 关键词过滤）")
class DeploymentListTest {

    private static final Pagination PAGE = new Pagination(1, 10);

    private static DeploymentSummary dep(String id, String name, String status) {
        return new DeploymentSummary(id, name, status, "1h", "A100 x1", 60L);
    }

    @Test
    @DisplayName("statusCounts：all=条数，各 status 计数正确")
    void statusCounts() {
        DeploymentList list = DeploymentList.ofListing(List.of(
                dep("d1", "alpha", "running"),
                dep("d2", "beta", "running"),
                dep("d3", "gamma", "completed"),
                dep("d4", "delta", "failed")), 4, PAGE);

        Map<String, Long> counts = list.statusCounts();
        assertEquals(4L, counts.get("all"));
        assertEquals(2L, counts.get("running"));
        assertEquals(1L, counts.get("completed"));
        assertEquals(1L, counts.get("failed"));
    }

    @Test
    @DisplayName("statusCounts：空/空白 status 归入 unknown 桶（不丢计数）")
    void statusCountsUnknownBucket() {
        DeploymentList list = DeploymentList.ofListing(List.of(
                dep("d1", "alpha", "running"),
                dep("d2", "beta", null),
                dep("d3", "gamma", "  ")), 3, PAGE);

        Map<String, Long> counts = list.statusCounts();
        assertEquals(3L, counts.get("all"));
        assertEquals(1L, counts.get("running"));
        assertEquals(2L, counts.get("unknown"), "null + 空白 → unknown");
    }

    @Test
    @DisplayName("statusCounts：空列表 → 仅 all=0")
    void statusCountsEmpty() {
        DeploymentList list = DeploymentList.ofListing(List.of(), 0, PAGE);
        assertEquals(0L, list.statusCounts().get("all"));
    }

    @Test
    @DisplayName("filterByKeyword：小写包含匹配；total 修正为过滤数量；page/pageSize 保留")
    void filterByKeyword() {
        DeploymentList list = DeploymentList.ofListing(List.of(
                dep("d1", "Prod-Alpha", "running"),
                dep("d2", "prod-beta", "running"),
                dep("d3", "Staging", "completed")), 99, PAGE);

        DeploymentList filtered = list.filterByKeyword("PROD");
        assertEquals(2, filtered.items().size(), "大小写不敏感匹配 prod");
        assertEquals(2L, filtered.total(), "total 修正为过滤后数量（契约 F-3042）");
        assertEquals(1, filtered.page());
        assertEquals(10, filtered.pageSize());
    }

    @Test
    @DisplayName("filterByKeyword：空/空白关键词 → 不过滤，原样返回（total 仍是上游 total）")
    void filterByKeywordEmpty() {
        DeploymentList list = DeploymentList.ofListing(List.of(
                dep("d1", "alpha", "running"),
                dep("d2", "beta", "running")), 50, PAGE);

        assertEquals(50L, list.filterByKeyword(null).total(), "null → 不过滤，保留上游 total");
        assertEquals(50L, list.filterByKeyword("   ").total(), "空白 → 不过滤");
        assertEquals(2, list.filterByKeyword("").items().size());
    }

    @Test
    @DisplayName("filterByKeyword：无匹配 → 空列表 + total=0")
    void filterByKeywordNoMatch() {
        DeploymentList list = DeploymentList.ofListing(List.of(
                dep("d1", "alpha", "running")), 1, PAGE);
        DeploymentList filtered = list.filterByKeyword("zzz");
        assertTrue(filtered.items().isEmpty());
        assertEquals(0L, filtered.total());
    }

    @Test
    @DisplayName("DeploymentSummary.nameContains：name 为空 → 不匹配任何非空关键词")
    void nameContainsNullName() {
        DeploymentSummary s = dep("d1", null, "running");
        assertEquals(false, s.nameContains("x"));
    }
}
