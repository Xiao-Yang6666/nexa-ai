package com.nexa.interfaces.oauthprovider.api.dto;

import com.nexa.domain.oauthprovider.vo.OAuthEndpoints;

/**
 * Discovery 结果 DTO（对齐 openapi discovery 200 的 {@code data}，F-1023）。
 *
 * @param authorizationEndpoint 授权端点
 * @param tokenEndpoint         令牌端点
 * @param userinfoEndpoint      用户信息端点
 */
public record DiscoveryVO(String authorizationEndpoint,
                            String tokenEndpoint,
                            String userinfoEndpoint) {

    /**
     * 从端点值对象投影为视图。
     *
     * @param endpoints 端点三元组
     * @return discovery 视图
     */
    public static DiscoveryVO from(OAuthEndpoints endpoints) {
        return new DiscoveryVO(
                endpoints.authorizationEndpoint(),
                endpoints.tokenEndpoint(),
                endpoints.userinfoEndpoint());
    }
}
