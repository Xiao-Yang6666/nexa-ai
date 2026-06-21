package com.nexa.model.interfaces.api.dto;

/** F-6002 更新 A→B 映射请求（A 不可改，无 public_name）。 */
public record PlatformModelMappingUpdateRequest(
        Long id,
        String upstreamName,
        Boolean enabled,
        String remark
) {}
