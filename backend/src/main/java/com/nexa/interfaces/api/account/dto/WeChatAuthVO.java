package com.nexa.interfaces.api.account.dto;

/**
 * WeChat 扫码授权发起态 DTO（对齐 openapi {@code GET /api/oauth/wechat} 的授权态，F-1021）。
 *
 * <p>微信不走标准重定向，而是前端据本载荷拼出二维码并轮询扫码结果（PRD AC-5 §3 W2 展示二维码）。
 * 本载荷只下发<b>非敏感</b>的前端拼码所需参数（appid/scope/redirect/state）；{@code app_secret} 绝不下发。
 * {@code enabled=false} 时（未配置）前端据此进入「微信未配置不可用态」（AC-5 §7）。</p>
 *
 * @param enabled     微信功能是否已配置可用（WeChatAuthEnabled 语义）
 * @param appId       微信 appid（前端拼二维码用，非敏感）
 * @param scope       授权 scope（如 snsapi_login）
 * @param redirectUri 授权回调地址（前端拼码用）
 * @param state       CSRF state（前端带它轮询/回调比对）
 */
public record WeChatAuthVO(boolean enabled,
                             String appId,
                             String scope,
                             String redirectUri,
                             String state) {
}
