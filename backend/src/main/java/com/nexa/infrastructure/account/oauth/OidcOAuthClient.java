package com.nexa.infrastructure.account.oauth;

import com.nexa.application.account.port.OAuthClient;
import com.nexa.application.account.port.OAuthUserInfo;
import com.nexa.domain.account.exception.InvalidCredentialException;
import com.nexa.domain.account.vo.OAuthProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 通用 OIDC OAuth 客户端实现（基础设施层适配器，F-1019）。
 *
 * <p>实现 {@link OAuthClient}：授权码 → token → userinfo → 归一化 {@link OAuthUserInfo}。
 * OIDC 端点（token/userinfo）由 provider discovery 决定，本切片以配置直配端点 URL；
 * 用例只依赖端口，OIDC 差异封装在本类（backend-engineer §2.3 + 策略模式）。</p>
 *
 * <p><b>需配 provider 凭证 + 端点</b>：{@code nexa.oauth.oidc.client-id/client-secret/token-uri/userinfo-uri/
 * redirect-uri} 从配置读，缺省不在启动期报错，仅在真实换 token 时因缺配置抛
 * {@link InvalidCredentialException}（wrap 带上下文）。</p>
 *
 * <p>OIDC 标准约定：
 * <ul>
 *   <li>token：{@code POST {token-uri}}（form: grant_type=authorization_code）</li>
 *   <li>userinfo：{@code GET {userinfo-uri}}（Bearer token）</li>
 *   <li>字段映射：{@code sub}（OIDC 主体标识，→ providerUserId）、
 *       {@code preferred_username}/{@code name}（→ username）、{@code email}（→ email，可空）</li>
 * </ul></p>
 */
@Component
public class OidcOAuthClient implements OAuthClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUri;
    private final String userinfoUri;
    private final String redirectUri;

    /**
     * @param clientId     OIDC client id（配置 nexa.oauth.oidc.client-id，缺省占位）
     * @param clientSecret OIDC client secret（配置 nexa.oauth.oidc.client-secret，缺省占位）
     * @param tokenUri     OIDC token 端点（配置 nexa.oauth.oidc.token-uri，缺省占位）
     * @param userinfoUri  OIDC userinfo 端点（配置 nexa.oauth.oidc.userinfo-uri，缺省占位）
     * @param redirectUri  OIDC 回调地址（配置 nexa.oauth.oidc.redirect-uri，缺省占位）
     */
    public OidcOAuthClient(
            @Value("${nexa.oauth.oidc.client-id:}") String clientId,
            @Value("${nexa.oauth.oidc.client-secret:}") String clientSecret,
            @Value("${nexa.oauth.oidc.token-uri:}") String tokenUri,
            @Value("${nexa.oauth.oidc.userinfo-uri:}") String userinfoUri,
            @Value("${nexa.oauth.oidc.redirect-uri:}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUri = tokenUri;
        this.userinfoUri = userinfoUri;
        this.redirectUri = redirectUri;
        this.restClient = RestClient.create();
    }

    /** {@inheritDoc} */
    @Override
    public OAuthProvider provider() {
        return OAuthProvider.OIDC;
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo exchangeCodeForUserInfo(String code) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()
                || tokenUri == null || tokenUri.isBlank() || userinfoUri == null || userinfoUri.isBlank()) {
            throw new InvalidCredentialException("oidc oauth client/endpoint configuration is incomplete");
        }
        try {
            // ① 授权码换 token（标准 OIDC authorization_code 流程）。
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            form.add("redirect_uri", redirectUri);
            Map<String, Object> tokenResp = restClient.post()
                    .uri(tokenUri)
                    .header("Accept", "application/json")
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            String accessToken = tokenResp == null ? null : (String) tokenResp.get("access_token");
            if (accessToken == null || accessToken.isBlank()) {
                throw new InvalidCredentialException("oidc did not return an access token");
            }

            // ② 拉 userinfo。
            Map<String, Object> user = restClient.get()
                    .uri(userinfoUri)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            if (user == null || user.get("sub") == null) {
                throw new InvalidCredentialException("oidc did not return a subject (sub)");
            }

            // ③ 归一化：sub 为 OIDC 主体唯一标识；用户名优先 preferred_username 再回退 name。
            String providerUserId = String.valueOf(user.get("sub"));
            String username = stringOrNull(user.get("preferred_username"));
            if (username == null) {
                username = stringOrNull(user.get("name"));
            }
            String email = stringOrNull(user.get("email"));
            return new OAuthUserInfo(providerUserId, username, email);
        } catch (InvalidCredentialException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidCredentialException("oidc oauth exchange failed: " + e.getMessage());
        }
    }

    private static String stringOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
