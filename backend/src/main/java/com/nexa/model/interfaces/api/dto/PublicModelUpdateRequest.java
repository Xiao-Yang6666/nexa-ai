package com.nexa.model.interfaces.api.dto;

import java.math.BigDecimal;

/** F-6001 更新对外模型请求（对齐 openapi PublicModelUpdateRequest；A 不可改，无 public_name）。 */
public record PublicModelUpdateRequest(
        Long id,
        String qualityTier,
        BigDecimal basePriceRatio,
        Boolean usePrice,
        BigDecimal basePrice,
        Boolean enabled,
        String displayName,
        Integer sortOrder
) {}
