package com.nexa.interfaces.api.account.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * WeChat 绑定/登录请求 DTO（对齐 openapi {@code POST /api/oauth/wechat/bind} requestBody，F-1022）。
 *
 * @param code 微信扫码授权后拿到的授权码（必填）
 */
public record WeChatBindRequest(@NotBlank(message = "code must not be blank") String code) {
}
