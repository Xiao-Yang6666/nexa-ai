package com.nexa.application.account;

import com.nexa.domain.account.model.User;

/**
 * 登录用例的返回结果（应用层产出）。
 *
 * <p>携带认证通过的用户聚合 + 签发的访问令牌。接口层据此组装 UserView DTO 与
 * 下发 token（绝不下发 password/access_token，见产品铁律）。</p>
 *
 * @param user  已认证用户聚合
 * @param token 已签发的访问令牌（JWT）
 */
public record LoginResult(User user, String token) {
}
