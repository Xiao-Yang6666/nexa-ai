package com.nexa.account.infrastructure.oauth;

import com.nexa.account.application.port.OAuthClient;
import com.nexa.account.application.port.OAuthUserInfo;
import com.nexa.account.domain.exception.InvalidCredentialException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * GitHub OAuth 客户端实现（基础设施层适配器，F-1016/F-1017）。
 *
 * <p>实现应用层端口 {@link OAuthClient}：用授权码向 GitHub token 端点换 access_token，再向 user 端点
 * 拉取用户信息，归一化为 {@link OAuthUserInfo}（providerUserId = GitHub 数字 id）。差异（端点 URL、
 * 字段映射、token 请求形式）封装在本类，OAuth 登录用例只依赖端口（backend-engineer §2.3 + 策略模式）。</p>
 *
 * <p><b>需配 provider 凭证</b>：{@code nexa.oauth.github.client-id/client-secret} 从配置读（@Value）。
 * 缺省时不在<b>启动期</b>报错（占位默认值），仅在<b>真实换 token 时</b>因缺凭证/网络失败抛
 * {@link InvalidCredentialException}（wrap 带 provider 上下文，不吞错）。这样单 provider 未配置不影响
 * 整体启动与编译（对齐任务约束「配置缺省时不报错」）。</p>
 *
 * <p>GitHub 真实端点：
 * <ul>
 *   <li>token：{@code POST https://github.com/login/oauth/access_token}（Accept: application/json）</li>
 *   <li>userinfo：{@code GET https://api.github.com/user}（Authorization: Bearer {token}）</li>
 *   <li>字段映射：{@code id}（数字，→ providerUserId）、{@code login}（→ username）、{@code email}（→ email，可空）</li>
 * </ul></p>
 */
@Component
public class GitHubOAuthClient implements OAuthClient {

    private static final String TOKEN_ENDPOINT = "https://github.com/login/oauth/access_token";
    private static final String USER_ENDPOINT = "https://api.github.com/user";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    /**
     * @param clientId     GitHub OAuth App client id（配置 nexa.oauth.github.client-id，缺省占位）
     * @param clientSecret GitHub OAuth App client secret（配置 nexa.oauth.github.client-secret，缺省占位）
     */
    public GitHubOAuthClient(
            @Value("${nexa.oauth.github.client-id:}") String clientId,
            @Value("${nexa.oauth.github.client-secret:}") String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restClient = RestClient.create();
    }

    /** {@inheritDoc} */
    @Override
    public com.nexa.account.domain.vo.OAuthProvider provider() {
        return com.nexa.account.domain.vo.OAuthProvider.GITHUB;
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo exchangeCodeForUserInfo(String code) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            // 凭证未配置：明确拒绝（接口层映射 400），不静默继续发无效请求。
            throw new InvalidCredentialException("github oauth client credentials not configured");
        }
        try {
            // ① 授权码换 access_token（GitHub 要求 form 表单 + Accept json 才返回 json）。
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("code", code);
            Map<String, Object> tokenResp = restClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .header("Accept", "application/json")
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            String accessToken = tokenResp == null ? null : (String) tokenResp.get("access_token");
            if (accessToken == null || accessToken.isBlank()) {
                throw new InvalidCredentialException("github did not return an access token");
            }

            // ② access_token 拉 userinfo。
            Map<String, Object> user = restClient.get()
                    .uri(USER_ENDPOINT)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(Map.class);
            if (user == null || user.get("id") == null) {
                throw new InvalidCredentialException("github did not return a user id");
            }

            // ③ 归一化：id 为数字，统一转字符串作 providerUserId（绑定锚点）。
            String providerUserId = String.valueOf(user.get("id"));
            String username = stringOrNull(user.get("login"));
            String email = stringOrNull(user.get("email"));
            return new OAuthUserInfo(providerUserId, username, email);
        } catch (InvalidCredentialException e) {
            throw e;
        } catch (RuntimeException e) {
            // 网络/解析错误 wrap 带 provider 上下文向上抛（不吞错），由 GlobalExceptionHandler 翻译。
            throw new InvalidCredentialException("github oauth exchange failed: " + e.getMessage());
        }
    }

    private static String stringOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
