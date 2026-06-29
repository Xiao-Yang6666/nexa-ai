package com.nexa.application.account;

import com.nexa.application.account.port.OAuthStateStore;
import com.nexa.domain.account.vo.OAuthState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * WeChat 扫码授权发起用例（应用服务，F-1021）。
 *
 * <p>对齐 openapi {@code GET /api/oauth/wechat}「授权态」+ PRD AC-5 §3（W1 判微信是否配置 → W2 展示二维码）。
 * 本用例：① 判微信功能是否已配置（appId 非空，对应 {@code WeChatAuthEnabled}）；② 生成并暂存一个 CSRF state
 * 供前端拼二维码 + 轮询/回调比对（复用 {@link OAuthStateStore}，与标准 OAuth 一致的 state 防 CSRF）；
 * ③ 返回前端拼码所需的非敏感参数载荷。</p>
 *
 * <p>设计说明：二维码图像本身由前端据 appId/redirect/state 拼出微信开放平台 URL 生成（开放平台 JS），
 * 后端不直接出图（避免后端依赖微信图床/截图）。后端职责是「判可用 + 发 state + 给参数」，
 * 扫码后的换取在 {@code POST /oauth/wechat/bind} 经 {@link OAuthLoginUseCase} 完成（W5）。</p>
 */
@Service
public class InitWeChatAuthUseCase {

    private final OAuthStateStore stateStore;
    private final String appId;
    private final String scope;
    private final String redirectUri;

    /**
     * @param stateStore  state 暂存端口（CSRF）
     * @param appId       微信 appid（配置 nexa.oauth.wechat.app-id，缺省占位 → 未配置）
     * @param scope       授权 scope（配置 nexa.oauth.wechat.scope，缺省 snsapi_login）
     * @param redirectUri 回调地址（配置 nexa.oauth.wechat.redirect-uri，缺省占位）
     */
    public InitWeChatAuthUseCase(
            OAuthStateStore stateStore,
            @Value("${nexa.oauth.wechat.app-id:}") String appId,
            @Value("${nexa.oauth.wechat.scope:snsapi_login}") String scope,
            @Value("${nexa.oauth.wechat.redirect-uri:}") String redirectUri) {
        this.stateStore = stateStore;
        this.appId = appId;
        this.scope = scope;
        this.redirectUri = redirectUri;
    }

    /**
     * 发起微信扫码授权：判可用 + 生成暂存 state + 返回拼码参数。
     *
     * @return 发起态结果（含 enabled / appId / scope / redirect / state；未配置时 enabled=false 其余空）
     */
    public WeChatAuthInitResult init() {
        boolean enabled = appId != null && !appId.isBlank();
        if (!enabled) {
            // 未配置：前端进「微信未配置不可用态」，不生成 state（无意义）。
            return new WeChatAuthInitResult(false, null, null, null, null);
        }
        // 生成并暂存 CSRF state（微信扫码同样需要 state 防 CSRF/重放，回调比对沿用 StateStore）。
        OAuthState state = OAuthState.generate(null);
        stateStore.save(state);
        return new WeChatAuthInitResult(true, appId, scope, redirectUri, state.token());
    }

    /**
     * WeChat 发起态结果（应用层返回，接口层投影为 {@code WeChatAuthView}）。
     *
     * @param enabled     是否已配置可用
     * @param appId       微信 appid（非敏感）
     * @param scope       授权 scope
     * @param redirectUri 回调地址
     * @param state       CSRF state token
     */
    public record WeChatAuthInitResult(boolean enabled,
                                       String appId,
                                       String scope,
                                       String redirectUri,
                                       String state) {
    }
}
