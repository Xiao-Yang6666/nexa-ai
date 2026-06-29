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
 * Discord OAuth 客户端实现（基础设施层适配器，F-1018）。
 *
 * <p>实现 {@link OAuthClient}：授权码 → access_token → userinfo → 归一化 {@link OAuthUserInfo}
 * （providerUserId = Discord snowflake id）。OAuth 登录用例只依赖端口，Discord 差异封装在本类
 * （backend-engineer §2.3 + 策略模式）。</p>
 *
 * <p><b>需配 provider 凭证</b>：{@code nexa.oauth.discord.client-id/client-secret/redirect-uri} 从配置读，
 * 缺省不在启动期报错，仅在真实换 token 时因缺凭证抛 {@link InvalidCredentialException}（wrap 带上下文）。</p>
 *
 * <p>Discord 真实端点：
 * <ul>
 *   <li>token：{@code POST https://discord.com/api/oauth2/token}（form: grant_type=authorization_code）</li>
 *   <li>userinfo：{@code GET https://discord.com/api/users/@me}（Bearer token）</li>
 *   <li>字段映射：{@code id}（snowflake，→ providerUserId）、{@code username}（→ username）、{@code email}（→ email，可空）</li>
 * </ul></p>
 */
@Component
public class DiscordOAuthClient implements OAuthClient {

    private static final String TOKEN_ENDPOINT = "https://discord.com/api/oauth2/token";
    private static final String USER_ENDPOINT = "https://discord.com/api/users/@me";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    /**
     * @param clientId     Discord App client id（配置 nexa.oauth.discord.client-id，缺省占位）
     * @param clientSecret Discord App client secret（配置 nexa.oauth.discord.client-secret，缺省占位）
     * @param redirectUri  Discord App 回调地址（配置 nexa.oauth.discord.redirect-uri，缺省占位）
     */
    public DiscordOAuthClient(
            @Value("${nexa.oauth.discord.client-id:}") String clientId,
            @Value("${nexa.oauth.discord.client-secret:}") String clientSecret,
            @Value("${nexa.oauth.discord.redirect-uri:}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.restClient = RestClient.create();
    }

    /** {@inheritDoc} */
    @Override
    public OAuthProvider provider() {
        return OAuthProvider.DISCORD;
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo exchangeCodeForUserInfo(String code) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new InvalidCredentialException("discord oauth client credentials not configured");
        }
        try {
            // ① 授权码换 token（Discord 要求标准 OAuth2 form 参数）。
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            form.add("redirect_uri", redirectUri);
            Map<String, Object> tokenResp = restClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .header("Accept", "application/json")
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            String accessToken = tokenResp == null ? null : (String) tokenResp.get("access_token");
            if (accessToken == null || accessToken.isBlank()) {
                throw new InvalidCredentialException("discord did not return an access token");
            }

            // ② 拉 userinfo。
            Map<String, Object> user = restClient.get()
                    .uri(USER_ENDPOINT)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            if (user == null || user.get("id") == null) {
                throw new InvalidCredentialException("discord did not return a user id");
            }

            // ③ 归一化。
            String providerUserId = String.valueOf(user.get("id"));
            String username = stringOrNull(user.get("username"));
            String email = stringOrNull(user.get("email"));
            return new OAuthUserInfo(providerUserId, username, email);
        } catch (InvalidCredentialException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidCredentialException("discord oauth exchange failed: " + e.getMessage());
        }
    }

    private static String stringOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
