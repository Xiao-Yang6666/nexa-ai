package com.nexa.account.interfaces.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求 DTO（接口层入参）。
 *
 * <p>对齐 openapi.yaml {@code POST /api/user/login}（F-1002）请求 schema：
 * {@code username} + {@code password} 必填。接口层仅做非空协议校验，
 * 长度/格式语义由领域值对象兜（登录侧刻意不回显具体非法原因，防枚举）。</p>
 *
 * @param username 用户名（必填）
 * @param password 明文密码（必填）
 */
public record LoginRequest(
        @NotBlank(message = "username must not be blank")
        String username,

        @NotBlank(message = "password must not be blank")
        String password) {
}
