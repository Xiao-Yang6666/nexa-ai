package com.nexa.infrastructure.account.oauth;

import com.nexa.application.account.port.OAuthClient;
import com.nexa.application.account.port.OAuthUserInfo;
import com.nexa.domain.account.exception.InvalidCredentialException;
import com.nexa.domain.account.vo.OAuthProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * WeChat 扫码授权 OAuth 客户端实现（基础设施层适配器，F-1021/1022）。
 *
 * <p>实现 {@link OAuthClient}：把微信「扫码授权拿到的 code → access_token + openid → userinfo」流程
 * 归一化为 {@link OAuthUserInfo}。微信与标准 OAuth 的差异（扫码态、二维码、轮询）由接口/前端承载
 * （二维码展示 + 轮询在 {@code GET /api/oauth/wechat} 与前端配合），后端这里只处理 <b>bind 端点</b>
 * （{@code POST /api/oauth/wechat/bind}）拿到 code 之后的换取流程——这正是 {@link OAuthClient} 的形状。</p>
 *
 * <p>微信 OAuth2（开放平台/网页授权）约定：
 * <ul>
 *   <li>token：{@code GET {token-uri}?appid=&secret=&code=&grant_type=authorization_code}，
 *       返回 {@code {access_token, openid, unionid?}}。</li>
 *   <li>userinfo：{@code GET {userinfo-uri}?access_token=&openid=}，返回 {@code {openid, unionid?, nickname}}。</li>
 *   <li>归一化：优先 {@code unionid}（跨应用稳定）作 providerUserId，回退 {@code openid}；{@code nickname}→username；
 *       微信不返邮箱（email 置空）。</li>
 * </ul></p>
 *
 * <p><b>需配凭证 + 端点</b>：{@code nexa.oauth.wechat.app-id/app-secret/token-uri/userinfo-uri} 从配置读，
 * 缺省不在启动期报错，仅在真实换 token 时因缺配置抛 {@link InvalidCredentialException}（wrap 带上下文）。
 * 端点 URI 可配置便于对接开放平台/公众号不同主机与测试桩。</p>
 */
@Component
public class WeChatOAuthClient implements OAuthClient {

    /** 微信 token 端点缺省（开放平台网站应用 sns 接口）。 */
    private static final String DEFAULT_TOKEN_URI =
            "https://api.weixin.qq.com/sns/oauth2/access_token";

    /** 微信 userinfo 端点缺省。 */
    private static final String DEFAULT_USERINFO_URI =
            "https://api.weixin.qq.com/sns/userinfo";

    private final RestClient restClient;
    private final String appId;
    private final String appSecret;
    private final String tokenUri;
    private final String userinfoUri;

    /**
     * @param appId       微信 appid（配置 nexa.oauth.wechat.app-id，缺省占位）
     * @param appSecret   微信 secret（配置 nexa.oauth.wechat.app-secret，缺省占位）
     * @param tokenUri    token 端点（配置 nexa.oauth.wechat.token-uri，缺省官方 sns 端点）
     * @param userinfoUri userinfo 端点（配置 nexa.oauth.wechat.userinfo-uri，缺省官方 sns 端点）
     */
    public WeChatOAuthClient(
            @Value("${nexa.oauth.wechat.app-id:}") String appId,
            @Value("${nexa.oauth.wechat.app-secret:}") String appSecret,
            @Value("${nexa.oauth.wechat.token-uri:" + DEFAULT_TOKEN_URI + "}") String tokenUri,
            @Value("${nexa.oauth.wechat.userinfo-uri:" + DEFAULT_USERINFO_URI + "}") String userinfoUri) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.tokenUri = tokenUri;
        this.userinfoUri = userinfoUri;
        this.restClient = RestClient.create();
    }

    /** {@inheritDoc} */
    @Override
    public OAuthProvider provider() {
        return OAuthProvider.WECHAT;
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo exchangeCodeForUserInfo(String code) {
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            // 微信功能未配置（对齐 PRD AC-5 §2「WeChatAuthEnabled 未配置则入口不可用」）。
            throw new InvalidCredentialException("wechat oauth app credentials are not configured");
        }
        try {
            // ① 用 code 换 access_token + openid（微信用 GET + query 参数，区别于标准 POST form）。
            String tokenUrl = tokenUri
                    + "?appid=" + enc(appId)
                    + "&secret=" + enc(appSecret)
                    + "&code=" + enc(code)
                    + "&grant_type=authorization_code";
            Map<String, Object> tokenResp = restClient.get()
                    .uri(tokenUrl)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(Map.class);
            if (tokenResp == null || tokenResp.get("errcode") != null) {
                // 微信失败响应形如 {errcode, errmsg}：不吞错，带 errmsg 上下文抛出。
                String errmsg = tokenResp == null ? "empty response" : String.valueOf(tokenResp.get("errmsg"));
                throw new InvalidCredentialException("wechat token exchange failed: " + errmsg);
            }
            String accessToken = stringOrNull(tokenResp.get("access_token"));
            String openid = stringOrNull(tokenResp.get("openid"));
            if (accessToken == null || accessToken.isBlank() || openid == null || openid.isBlank()) {
                throw new InvalidCredentialException("wechat did not return access_token/openid");
            }
            String unionid = stringOrNull(tokenResp.get("unionid"));

            // ② 拉 userinfo（取昵称作候选用户名；微信不返邮箱）。
            String userUrl = userinfoUri
                    + "?access_token=" + enc(accessToken)
                    + "&openid=" + enc(openid);
            Map<String, Object> user = restClient.get()
                    .uri(userUrl)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(Map.class);

            // ③ 归一化：providerUserId 优先 unionid（跨应用稳定），回退 openid；nickname→username。
            String providerUserId = (unionid != null && !unionid.isBlank()) ? unionid : openid;
            String username = user == null ? null : stringOrNull(user.get("nickname"));
            // 微信不提供邮箱，email 置空（建号时不落邮箱，对齐 PRD AC-5 §5 仅写 wechat 绑定标识）。
            return new OAuthUserInfo(providerUserId, username, null);
        } catch (InvalidCredentialException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidCredentialException("wechat oauth exchange failed: " + e.getMessage());
        }
    }

    private static String enc(String v) {
        return java.net.URLEncoder.encode(v == null ? "" : v, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String stringOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
