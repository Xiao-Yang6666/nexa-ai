package com.nexa.interfaces.api.model.dto;

/** F-6003 创建 C→A 映射请求（对齐 openapi UserModelAliasCreateRequest；scope_id 服务端强制推导）。 */
public record UserModelAliasCreateRequest(
        String scopeType,
        String alias,
        String target,
        Boolean enabled
) {}
