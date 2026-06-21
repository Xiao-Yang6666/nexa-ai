package com.nexa.model.interfaces.api.dto;

/** F-6002 创建 A→B 映射请求（对齐 openapi PlatformModelMappingCreateRequest）。 */
public record PlatformModelMappingCreateRequest(
        String publicName,
        String upstreamName,
        Boolean enabled,
        String remark
) {}
