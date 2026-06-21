package com.nexa.model.interfaces.api.dto;

import com.nexa.model.domain.model.PublicModel;

import java.math.BigDecimal;

/**
 * 对外模型商品目录管理视图（接口层出参，AdminView，F-6001）。
 *
 * <p>对齐 openapi PublicModelAdminView。含售价/上下架/品质/时间戳，无敏感信息（A 本就公开）。</p>
 */
public record PublicModelAdminView(
        Long id,
        String publicName,
        String qualityTier,
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
    public static PublicModelAdminView from(PublicModel m) {
        return new PublicModelAdminView(m.id(), m.publicName(), m.qualityTier(),
                m.basePriceRatio(), m.usePrice(), m.basePrice(), m.enabled(),
                m.displayName(), m.sortOrder(), m.description(),
                m.createdTime(), m.updatedTime());
    }
}
