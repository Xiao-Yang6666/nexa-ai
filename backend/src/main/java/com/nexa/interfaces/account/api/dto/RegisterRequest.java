package com.nexa.interfaces.account.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求 DTO（接口层入参）。
 *
 * <p>对齐 openapi.yaml {@code POST /api/user/register}（F-1001）请求 schema：
 * {@code username}（maxLength 20）、{@code password}（minLength 8, maxLength 20）必填，
 * {@code email}（maxLength 50）、{@code verification_code}、{@code aff_code} 可选。
 * Bean Validation 注解在接口层兜协议级校验；领域级语义校验仍由值对象负责（双层防御）。</p>
 *
 * <p>JSON 字段名按 openapi 用 snake_case（{@code verification_code}/{@code aff_code}），
 * 由全局 Jackson {@code PropertyNamingStrategy} 统一转换（见 application.yml）。</p>
 *
 * @param username         用户名（必填，≤20）
 * @param password         明文密码（必填，8~20）
 * @param email            邮箱（可选，≤50）
 * @param verificationCode 邮箱验证码（可选；EmailVerificationEnabled 时业务侧必填）
 * @param affCode          邀请码（可选）
 */
public record RegisterRequest(
        @NotBlank(message = "username must not be blank")
        @Size(max = 20, message = "username length must be <= 20")
        String username,

        @NotBlank(message = "password must not be blank")
        @Size(min = 8, max = 20, message = "password length must be between 8 and 20")
        String password,

        @Size(max = 50, message = "email length must be <= 50")
        String email,

        String verificationCode,

        String affCode) {
}
