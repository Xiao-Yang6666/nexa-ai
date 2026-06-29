package com.nexa.domain.deployment.model;

import java.util.Map;

/**
 * 硬件类型只读视图（io.net 硬件规格，F-3049）。
 *
 * <p>领域模型，零框架依赖。承载「硬件类型查询」单条记录。io.net 硬件规格字段繁多且可能演进，
 * 用 {@code attributes} 透传上游原始键值（如 gpu 型号、单价、可用副本数等），避免每次上游加字段
 * 都要改契约——本端点为参考性元数据查询（API-ENDPOINTS §10.3「实际计费以 io.net 为准」）。</p>
 *
 * <p>{@code id} 与 {@code availableReplicas} 提取为强类型字段：前者是后续 replicas 查询的锚点，
 * 后者用于聚合「总可用副本数」（{@code total_available}）。</p>
 *
 * @param id                硬件类型 ID
 * @param name              硬件类型名称（可空）
 * @param availableReplicas 该类型可用副本数（用于汇总 total_available）
 * @param attributes        上游原始属性透传（只读）
 */
public record HardwareType(long id, String name, long availableReplicas, Map<String, Object> attributes) {

    /**
     * 紧凑构造器：防御式拷贝 attributes 为不可变映射，保证值对象不可变。
     */
    public HardwareType {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
