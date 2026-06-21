package com.nexa.deployment.domain.model;

import java.util.List;

/**
 * 部署地域查询结果聚合（充血领域模型，F-3050）。
 *
 * <p>承载「部署地域查询」整体结果并守护聚合不变量：契约出参 {@code { locations[], total }}
 * （API-ENDPOINTS §10.3）。关键领域规则：<b>上游 total=0 时回退 len(Locations)</b>——上游偶发不回
 * 总数，本聚合用列表长度兜底，保证 total 永远反映实际可见地域数（API-ENDPOINTS §10.3 F-3050
 * 「上游 total=0 时回退 len(Locations)」）。</p>
 *
 * @param locations     地域列表（只读）
 * @param upstreamTotal 上游声明的总数（可能为 0/不可靠）
 */
public record LocationCatalog(List<Location> locations, long upstreamTotal) {

    /**
     * 紧凑构造器：防御式拷贝列表为不可变列表。
     */
    public LocationCatalog {
        locations = locations == null ? List.of() : List.copyOf(locations);
    }

    /**
     * 地域总数（契约：上游 total&gt;0 用上游值，否则回退列表长度）。
     *
     * <p>领域规则来源：API-ENDPOINTS §10.3 F-3050 兜底规则。</p>
     *
     * @return 生效的地域总数
     */
    public long total() {
        return upstreamTotal > 0 ? upstreamTotal : locations.size();
    }
}
