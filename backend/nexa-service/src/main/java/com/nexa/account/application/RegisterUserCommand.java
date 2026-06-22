package com.nexa.account.application;

/**
 * 注册命令（应用层入参 DTO）。
 *
 * <p>承载接口层翻译后的注册请求原始字段（对齐 openapi F-1001 register schema）。
 * 应用层据此构造领域值对象并编排注册用例。{@code rawPassword} 为明文，仅在用例内即时哈希。</p>
 *
 * @param username         用户名（必填）
 * @param rawPassword      明文密码（必填）
 * @param email            邮箱（可选，可为 null/空白）
 * @param verificationCode 邮箱验证码（EmailVerificationEnabled 时必填，本切片透传不强校验）
 * @param affCode          邀请码（可选，F-1040 归因）
 */
public record RegisterUserCommand(
        String username,
        String rawPassword,
        String email,
        String verificationCode,
        String affCode) {
}
