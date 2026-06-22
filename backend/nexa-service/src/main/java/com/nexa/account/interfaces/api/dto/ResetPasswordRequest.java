package com.nexa.account.interfaces.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交重置新密码请求 DTO（接口层入参，F-1007）。
 *
 * <p>对齐 openapi.yaml {@code POST /api/user/reset} 请求 schema：
 * {@code email}、{@code token}、{@code password}（minLength 8, maxLength 20）均必填。
 * Bean Validation 兜协议级校验；领域级语义（邮箱格式/密码长度）仍由值对象兜（双层防御）。</p>
 *
 * <p>JSON 字段名按 openapi（{@code email}/{@code token}/{@code password}），无 snake_case 转换需求。
 * 出参不回显任何敏感信息（仅 SuccessResponse），令牌/密码绝不下发或记录。</p>
 *
 * @param email    重置邮箱（必填，与令牌双因子绑定）
 * @param token    一次性重置令牌（必填）
 * @param password 新明文密码（必填，8~20）
 */
public record ResetPasswordRequest(
        @NotBlank(message = "email must not be blank")
        @Size(max = 50, message = "email length must be <= 50")
        String email,

        @NotBlank(message = "token must not be blank")
        String token,

        @NotBlank(message = "password must not be blank")
        @Size(min = 8, max = 20, message = "password length must be between 8 and 20")
        String password) {
}
