package com.nexa.infrastructure.oauthprovider.oauth;

import com.nexa.application.oauthprovider.port.OidcDiscoveryClient;
import com.nexa.domain.oauthprovider.exception.InvalidCustomOAuthProviderException;
import com.nexa.domain.oauthprovider.vo.OAuthEndpoints;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * OIDC discovery 拉取实现（基础设施层适配器，F-1023）。
 *
 * <p>实现 {@link OidcDiscoveryClient}：由 issuer 拼出 well-known 路径、GET 拉取 discovery JSON、
 * 取三端点归一化为 {@link OAuthEndpoints}。OIDC 规范：
 * {@code {issuer}/.well-known/openid-configuration} 返回含 {@code authorization_endpoint /
 * token_endpoint / userinfo_endpoint} 的 JSON。网络/解析/缺字段失败均 wrap 带 issuer 上下文抛
 * {@link InvalidCustomOAuthProviderException}（不吞错，backend-engineer §3.2）。</p>
 */
@Component
public class HttpOidcDiscoveryClient implements OidcDiscoveryClient {

    /** OIDC discovery well-known 相对路径。 */
    private static final String WELL_KNOWN = "/.well-known/openid-configuration";

    private final RestClient restClient;

    /** 默认构造：用 Spring 的 RestClient（与账号域 OidcOAuthClient 同栈）。 */
    public HttpOidcDiscoveryClient() {
        this.restClient = RestClient.create();
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public OAuthEndpoints fetch(String issuer) {
        String url = buildWellKnownUrl(issuer);
        try {
            Map<String, Object> doc = restClient.get()
                    .uri(url)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(Map.class);
            if (doc == null) {
                throw new InvalidCustomOAuthProviderException(
                        "oidc discovery returned empty document for issuer: " + issuer);
            }
            // 归一化三端点；任一缺失/非法由 OAuthEndpoints 值对象在构造时兜底抛错。
            return OAuthEndpoints.of(
                    stringOf(doc.get("authorization_endpoint")),
                    stringOf(doc.get("token_endpoint")),
                    stringOf(doc.get("userinfo_endpoint")));
        } catch (InvalidCustomOAuthProviderException e) {
            // 端点缺失/非法已是领域语义，直接上抛（保留具体字段提示）。
            throw e;
        } catch (RuntimeException e) {
            // 网络/反序列化等运行时错误：wrap 带 issuer 上下文，不吞错。
            throw new InvalidCustomOAuthProviderException(
                    "oidc discovery fetch failed for issuer " + issuer + ": " + e.getMessage());
        }
    }

    /**
     * 由 issuer 拼出 well-known discovery URL（issuer 已含该后缀则不重复拼，去重尾部斜杠）。
     *
     * @param issuer issuer 基址
     * @return well-known 完整 URL
     */
    private static String buildWellKnownUrl(String issuer) {
        String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        if (base.endsWith(WELL_KNOWN)) {
            return base;
        }
        return base + WELL_KNOWN;
    }

    private static String stringOf(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
