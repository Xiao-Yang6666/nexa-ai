package com.nexa.oauthprovider.application;

import com.nexa.oauthprovider.application.port.OidcDiscoveryClient;
import com.nexa.oauthprovider.domain.exception.InvalidCustomOAuthProviderException;
import com.nexa.oauthprovider.domain.vo.OAuthEndpoints;
import org.springframework.stereotype.Service;

/**
 * 拉取自定义 OAuth provider 的 OIDC discovery 用例（应用服务，F-1023）。
 *
 * <p>对齐 openapi {@code POST /api/custom-oauth-provider/discovery}（RootAuth）：入参 {@code issuer}，
 * 出参 {@code {authorization_endpoint, token_endpoint, userinfo_endpoint}}。本用例校验 issuer 非空后
 * 委托 {@link OidcDiscoveryClient} 拉取 well-known 文档并归一化（IO 在基础设施实现）。
 * 端点合法性由 {@link OAuthEndpoints} 值对象在归一化时兜底。</p>
 */
@Service
public class FetchOidcDiscoveryUseCase {

    private final OidcDiscoveryClient discoveryClient;

    /**
     * @param discoveryClient OIDC discovery 拉取端口（基础设施实现）
     */
    public FetchOidcDiscoveryUseCase(OidcDiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    /**
     * 按 issuer 拉取 discovery 端点。
     *
     * @param issuer OIDC issuer 基址
     * @return 归一化的端点三元组
     * @throws InvalidCustomOAuthProviderException issuer 为空 / 非法
     */
    public OAuthEndpoints fetch(String issuer) {
        String v = issuer == null ? null : issuer.trim();
        if (v == null || v.isEmpty()) {
            throw new InvalidCustomOAuthProviderException("issuer must not be blank");
        }
        if (!(v.startsWith("http://") || v.startsWith("https://"))) {
            throw new InvalidCustomOAuthProviderException("issuer must be an http(s) URL");
        }
        // 拉取失败（网络/解析/缺字段）在 client 内 wrap 带 issuer 上下文抛出，不在此吞错。
        return discoveryClient.fetch(v);
    }
}
