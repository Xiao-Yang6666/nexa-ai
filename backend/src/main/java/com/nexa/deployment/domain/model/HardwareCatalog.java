package com.nexa.deployment.domain.model;

import java.util.List;

/**
 * 硬件类型查询结果聚合（充血领域模型，F-3049）。
 *
 * <p>承载「硬件类型查询」整体结果并<b>自守护聚合不变量</b>：契约出参
 * {@code { hardware_types[], total: <类型数>, total_available: <总可用副本数> }}
 * （API-ENDPOINTS §10.3）。{@code total}/{@code totalAvailable} 不是外部传入的裸字段，而是由本聚合
 * <b>从硬件类型列表派生计算</b>——避免「总数与列表不一致」这类贫血模型常见 bug
 * （backend-engineer §2.2 充血模型，计算逻辑在领域对象上）。</p>
 *
 * @param hardwareTypes 硬件类型列表（只读）
 */
public record HardwareCatalog(List<HardwareType> hardwareTypes) {

    /**
     * 紧凑构造器：防御式拷贝列表为不可变列表。
     */
    public HardwareCatalog {
        hardwareTypes = hardwareTypes == null ? List.of() : List.copyOf(hardwareTypes);
    }

    /**
     * 硬件类型总数（契约 {@code total}=类型数）。
     *
     * @return 类型数量
     */
    public int total() {
        return hardwareTypes.size();
    }

    /**
     * 总可用副本数（契约 {@code total_available}=各类型可用副本数之和）。
     *
     * <p>领域规则来源：API-ENDPOINTS §10.3 F-3049。由聚合自行求和，保证与列表内容一致。</p>
     *
     * @return 各硬件类型可用副本数之和
     */
    public long totalAvailable() {
        return hardwareTypes.stream().mapToLong(HardwareType::availableReplicas).sum();
    }
}
