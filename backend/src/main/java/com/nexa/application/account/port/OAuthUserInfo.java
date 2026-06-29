package com.nexa.application.account.port;

/**
 * 第三方 OAuth 用户信息（应用层端口返回的不可变载荷）。
 *
 * <p>{@link OAuthClient} 用授权码换取访问令牌、再拉取用户信息后，把各 provider 异构的 userinfo
 * 归一化为本载荷返回给 OAuth 登录用例。用例据 {@link #providerUserId()} 找绑定/建号，
 * 据 {@link #username()} / {@link #email()} 作为建号时的初始资料（F-1016~1020）。</p>
 *
 * @param providerUserId 第三方账号在该 provider 下的唯一 id（必填，绑定锚点）
 * @param username       第三方返回的用户名/登录名（建号时作候选用户名，可空）
 * @param email          第三方返回的邮箱（建号时落库，可空）
 */
public record OAuthUserInfo(String providerUserId, String username, String email) {
}
