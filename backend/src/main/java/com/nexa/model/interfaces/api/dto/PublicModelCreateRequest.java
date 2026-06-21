package com.nexa.model.interfaces.api.dto;

import java.math.BigDecimal;

/** F-6001 创建对外模型请求（对齐 openapi PublicModelCreateRequest）。 */
public record PublicModelCreateRequest(
        String publicName,
        String qualityTier,
        BigDecimal basePriceRatio,
        Boolean usePrice,
        BigDecimal basePrice,
        Boolean enabled,
        String displayName,
        Integer sortOrder,
        String description
) {}
