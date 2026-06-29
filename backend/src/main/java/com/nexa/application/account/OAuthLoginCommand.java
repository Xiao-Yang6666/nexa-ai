package com.nexa.application.account;

/**
 * OAuth 登录/绑定命令（接口层翻译后的入参，F-1016~1020）。
 *
 * <p>对齐 openapi 各 OAuth 回调端点的 query 参数：{@code provider}（路径段，F-1016 通用回调）、
 * {@code code}（授权码）、{@code state}（CSRF 比对）。{@code bindUserId} 非空表示「绑定」语义
 * （已登录用户把第三方账号绑到本账号）；为空表示「登录/注册」语义（未登录走找绑定或建号）。</p>
 *
 * @param provider   provider 标识串（github/discord/oidc/linuxdo）
 * @param code       第三方回调带回的授权码
 * @param state      回调带回的 state token（CSRF 校验）
 * @param bindUserId 绑定目标用户 id；{@code null}=登录/注册流程，非空=绑定流程
 */
public record OAuthLoginCommand(String provider, String code, String state, Long bindUserId) {
}
