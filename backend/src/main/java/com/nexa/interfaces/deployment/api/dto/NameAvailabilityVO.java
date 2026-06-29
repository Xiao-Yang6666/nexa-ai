package com.nexa.interfaces.deployment.api.dto;

import com.nexa.domain.deployment.model.NameAvailability;

/**
 * 集群名称可用性出参视图（F-3053）。
 *
 * <p>对齐契约出参 {@code { available: bool, name }}（API-ENDPOINTS §10.3）。</p>
 *
 * @param available 名称是否可用
 * @param name      被查询的名称（原样回带，防异步错位）
 */
public record NameAvailabilityVO(boolean available, String name) {

    /**
     * 从领域结果投影。
     *
     * @param na 名称可用性结果
     * @return 出参视图
     */
    public static NameAvailabilityVO from(NameAvailability na) {
        return new NameAvailabilityVO(na.available(), na.name());
    }
}
