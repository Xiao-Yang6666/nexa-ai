package com.nexa.deployment.interfaces.api.dto;

import com.nexa.deployment.domain.model.Location;
import com.nexa.deployment.domain.model.LocationCatalog;

import java.util.List;
import java.util.Map;

/**
 * 部署地域查询出参视图（管理端视图，F-3050）。
 *
 * <p>对齐契约出参 {@code { locations[], total }}（API-ENDPOINTS §10.3）。total 取自领域聚合
 * {@link LocationCatalog#total()} 的兜底计算（上游 0 时回退列表长度）。各地域透传上游属性（已脱敏）。</p>
 *
 * @param locations 地域属性列表（上游透传，已脱敏）
 * @param total     生效地域总数
 */
public record LocationsView(List<Map<String, Object>> locations, long total) {

    /**
     * 从领域聚合投影为出参视图。
     *
     * @param catalog 地域聚合
     * @return 出参视图
     */
    public static LocationsView from(LocationCatalog catalog) {
        List<Map<String, Object>> items = catalog.locations().stream()
                .map(Location::attributes)
                .toList();
        return new LocationsView(items, catalog.total());
    }
}
