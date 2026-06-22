package com.nexa.oauthprovider.interfaces.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Discovery 拉取请求 DTO（对齐 openapi {@code POST /api/custom-oauth-provider/discovery} requestBody，F-1023）。
 *
 * @param issuer OIDC issuer 基址（必填）
 */
public record DiscoveryRequest(@NotBlank(message = "issuer must not be blank") String issuer) {
}
