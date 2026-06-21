package com.nexa.account.infrastructure.oauth;

import com.nexa.account.application.port.OAuthClient;
import com.nexa.account.application.port.OAuthUserInfo;
import com.nexa.account.domain.exception.InvalidCredentialException;
import com.nexa.account.domain.vo.OAuthProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * LinuxDO OAuth 客户端实现（基础设施层适配器，F-1020）。
 *
 * <p>实现应用层端口 {@link OAuthClient}：用授权码向 LinuxDO（connect.linux.do）token 端点换
 * access_token，再向 userinfo 端点拉取用户信息，归一化为 {@link OAuthUserInfo}。LinuxDO 论坛登录
 * 基于标准 OAuth2 / OIDC 风格授权码流程（端点形态与 {@link OidcOAuthClient} 一致），差异（端点 URL、
 * 字段映射）封装在本类，OAuth 登录用例只依赖端口（backend-engineer §2.3 + 策略模式）。</p>
 *
 * <p><b>需配 provider 凭证</b>：{@code nexa.oauth.linuxdo.client-id/client-secret/redirect-uri} 从配置读
 * （@Value）。token/userinfo 端点默认指向 LinuxDO 官方地址，亦可由配置覆盖以适配私有部署。缺省凭证时
 * <b>不在启动期报错</b>（占位默认值），仅在<b>真实换 token 时</b>因缺凭证/网络失败抛
 * {@link InvalidCredentialException}（wrap 带 provider 上下文，不吞错）。这样单 provider 未配置不影响
 * 整体启动与编译（对齐任务约束「配置缺省时不报错」）。</p>
 *
 * <p>LinuxDO 端点（connect.linux.do，OAuth2 授权码流程）：
 * <ul>
 *   <li>token：{@code POST https://connect.linux.do/oauth2/token}（form: grant_type=authorization_code）</li>
 *   <li>userinfo：{@code GET https://connect.linux.do/api/user}（Bearer token）</li>
 *   <li>字段映射：{@code id}（论坛用户数字 id，→ providerUserId）、{@code username}（→ username）、
 *       {@code email}（→ email，可空）。LinuxDO 另返回 {@code trust_level}（信任级），本切片仅完成登录/绑定，
 *       信任级门槛校验留待后续 wave（TODO）。</li>
 * </ul></p>
 *
 * <p>TODO（F-1020 信任级校验）：LinuxDO userinfo 含 {@code trust_level}，PRD 要求按信任级设门槛。本切片
 * 先打通登录/建号主流程，信任级校验（拒绝低于阈值的用户）待后续 wave 接入配置化阈值后补齐。</p>
 */
@Component
public class LinuxDoOAuthClient implements OAuthClient {

    /** LinuxDO 默认 token 端点（可由配置 nexa.oauth.linuxdo.token-uri 覆盖）。 */
    private static final String DEFAULT_TOKEN_URI = "https://connect.linux.do/oauth2/token";

    /** LinuxDO 默认 userinfo 端点（可由配置 nexa.oauth.linuxdo.userinfo-uri 覆盖）。 */
    private static final String DEFAULT_USERINFO_URI = "https://connect.linux.do/api/user";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUri;
    private final String userinfoUri;
    private final String redirectUri;

    /**
     * @param clientId     LinuxDO OAuth client id（配置 nexa.oauth.linuxdo.client-id，缺省占位）
     * @param clientSecret LinuxDO OAuth client secret（配置 nexa.oauth.linuxdo.client-secret，缺省占位）
     * @param tokenUri     token 端点（配置 nexa.oauth.linuxdo.token-uri，缺省指向官方端点）
     * @param userinfoUri  userinfo 端点（配置 nexa.oauth.linuxdo.userinfo-uri，缺省指向官方端点）
     * @param redirectUri  回调地址（配置 nexa.oauth.linuxdo.redirect-uri，缺省占位）
     */
    public LinuxDoOAuthClient(
            @Value("${nexa.oauth.linuxdo.client-id:}") String clientId,
            @Value("${nexa.oauth.linuxdo.client-secret:}") String clientSecret,
            @Value("${nexa.oauth.linuxdo.token-uri:" + DEFAULT_TOKEN_URI + "}") String tokenUri,
            @Value("${nexa.oauth.linuxdo.userinfo-uri:" + DEFAULT_USERINFO_URI + "}") String userinfoUri,
            @Value("${nexa.oauth.linuxdo.redirect-uri:}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUri = (tokenUri == null || tokenUri.isBlank()) ? DEFAULT_TOKEN_URI : tokenUri;
        this.userinfoUri = (userinfoUri == null || userinfoUri.isBlank()) ? DEFAULT_USERINFO_URI : userinfoUri;
        this.redirectUri = redirectUri;
        this.restClient = RestClient.create();
    }

    /** {@inheritDoc} */
    @Override
    public OAuthProvider provider() {
        return OAuthProvider.LINUXDO;
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo exchangeCodeForUserInfo(String code) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            // 凭证未配置：明确拒绝（接口层映射 400），不静默继续发无效请求。
            throw new InvalidCredentialException("linuxdo oauth client credentials not configured");
        }
        try {
            // ① 授权码换 access_token（标准 OAuth2 authorization_code 流程，form 表单 + Accept json）。
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
                throw new InvalidCredentialException("linuxdo did not return an access token");
            }

            // ② access_token 拉 userinfo。
            Map<String, Object> user = restClient.get()
                    .uri(userinfoUri)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(Map.class);
            if (user == null || user.get("id") == null) {
                throw new InvalidCredentialException("linuxdo did not return a user id");
            }

            // ③ 归一化：id 为论坛数字 id，统一转字符串作 providerUserId（绑定锚点）。
            // TODO（F-1020）：user.get("trust_level") 信任级门槛校验留待后续 wave。
            String providerUserId = String.valueOf(user.get("id"));
            String username = stringOrNull(user.get("username"));
            String email = stringOrNull(user.get("email"));
            return new OAuthUserInfo(providerUserId, username, email);
        } catch (InvalidCredentialException e) {
            throw e;
        } catch (RuntimeException e) {
            // 网络/解析错误 wrap 带 provider 上下文向上抛（不吞错），由 GlobalExceptionHandler 翻译。
            throw new InvalidCredentialException("linuxdo oauth exchange failed: " + e.getMessage());
        }
    }

    private static String stringOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
