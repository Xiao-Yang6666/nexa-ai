package com.nexa.interfaces.deployment.api.dto;

import com.nexa.domain.deployment.model.HardwareCatalog;
import com.nexa.domain.deployment.model.HardwareType;

import java.util.List;
import java.util.Map;

/**
 * 硬件类型查询出参视图（管理端视图，F-3049）。
 *
 * <p>对齐契约出参 {@code { hardware_types[], total, total_available }}（API-ENDPOINTS §10.3）。
 * total/total_available 取自领域聚合 {@link HardwareCatalog} 的派生计算（接口层不重算，只投影）。
 * 各硬件类型透传上游属性（已在 infra 层剔除敏感键）。</p>
 *
 * @param hardwareTypes  硬件类型属性列表（上游透传，已脱敏）
 * @param total          硬件类型数
 * @param totalAvailable 总可用副本数
 */
public record HardwareTypesVO(
        List<Map<String, Object>> hardwareTypes,
        int total,
        long totalAvailable) {

    /**
     * 从领域聚合投影为出参视图。
     *
     * @param catalog 硬件类型聚合
     * @return 出参视图
     */
    public static HardwareTypesVO from(HardwareCatalog catalog) {
        List<Map<String, Object>> items = catalog.hardwareTypes().stream()
                .map(HardwareType::attributes)
                .toList();
        return new HardwareTypesVO(items, catalog.total(), catalog.totalAvailable());
    }
}
