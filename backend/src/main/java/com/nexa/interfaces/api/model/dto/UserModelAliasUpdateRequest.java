package com.nexa.interfaces.api.model.dto;

/** F-6003 更新 C→A 映射请求（幂等键不可改，仅 target/enabled）。 */
public record UserModelAliasUpdateRequest(
        String target,
        Boolean enabled
) {}
