package com.nexa.application.account.command;

/**
 * 提交重置新密码命令（接口层翻译后的入参，F-1007）。
 *
 * <p>对齐 openapi.yaml {@code POST /api/user/reset} 请求体 {@code {email, token, password}}。
 * 承载找回密码第二段的三要素：定位账号的邮箱、校验身份的一次性令牌、要设置的新明文密码。</p>
 *
 * @param email       提交重置的邮箱（与令牌双因子绑定）
 * @param token       一次性重置令牌（来自重置邮件）
 * @param newPassword 新明文密码（仅用于即时哈希，不落库不记录）
 */
public record ResetPasswordCommand(String email, String token, String newPassword) {
}
