package com.nexa.application.account;

import com.nexa.domain.account.model.User;

/**
 * OAuth 登录/绑定结果（应用层返回，F-1016~1020）。
 *
 * <p>承载流程结束后的用户聚合 + （登录场景）签发的访问令牌。接口层把 {@link #user()} 投影为
 * {@code UserVO}（客户视图零敏感字段）回给前端；{@link #token()} 不进 body（不下发 access_token，
 * 产品铁律，沿用 {@link LoginResult} 约定）。{@link #newlyCreated()} 标识本次是否新建了账号
 * （便于上层埋点/区分首登），不影响下发内容。</p>
 *
 * @param user         登录/绑定后的用户聚合
 * @param token        签发的访问令牌（登录场景）
 * @param newlyCreated 本次是否新建了账号（true=未绑定→建号+绑定；false=已绑定→直接登录/绑定）
 */
public record OAuthLoginResult(User user, String token, boolean newlyCreated) {
}
