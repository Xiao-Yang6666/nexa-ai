package com.nexa.application.telegram;

import com.nexa.domain.account.model.User;

/**
 * Telegram 登录/建号用例结果（应用层返回，F-1051）。
 *
 * <p>承载登录/建号后的用户聚合 + 已签发令牌 + 是否新建标志。沿用 {@code com.nexa.account.OAuthLoginResult}
 * 的约定：token 由用例签发并随结果返回，但<b>是否进 HTTP body 由接口层决定</b>（产品铁律：登录返回
 * 用户客户视图，token 走 cookie/header，不进 body）。</p>
 *
 * @param user         登录/建号后的用户聚合
 * @param token        为该用户签发的访问令牌（JWT）
 * @param newlyCreated 本次是否新建了账号（true=首次 Telegram 登录建号；false=已有绑定直接登录）
 */
public record TelegramLoginResult(User user, String token, boolean newlyCreated) {
}
