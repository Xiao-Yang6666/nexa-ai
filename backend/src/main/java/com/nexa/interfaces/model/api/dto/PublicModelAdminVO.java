package com.nexa.interfaces.model.api.dto;

import com.nexa.domain.model.model.PublicModel;

import java.math.BigDecimal;

/**
 * 对外模型商品目录管理视图（接口层出参，AdminView，F-6001）。
 *
 * <p>对齐 openapi PublicModelAdminVO。含售价/上下架/品质/时间戳，无敏感信息（A 本就公开）。</p>
 */
public record PublicModelAdminVO(
        Long id,
        String publicName,
        BigDecimal basePriceRatio,
        Boolean usePrice,
        BigDecimal basePrice,
        Boolean enabled,
        String displayName,
        Integer sortOrder,
        String description,
        Long createdTime,
        Long updatedTime
) {
    /** 由领域聚合裁剪为管理视图。 */
    public static PublicModelAdminVO from(PublicModel m) {
        return new PublicModelAdminVO(m.id(), m.publicName(),
                m.basePriceRatio(), m.usePrice(), m.basePrice(), m.enabled(),
                m.displayName(), m.sortOrder(), m.description(),
                m.createdTime(), m.updatedTime());
    }
}
