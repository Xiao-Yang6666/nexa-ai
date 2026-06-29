package com.nexa.application.account;

/**
 * 登录命令（应用层入参 DTO）。
 *
 * <p>对齐 openapi F-1002 login schema：username + password 必填。</p>
 *
 * @param username    用户名
 * @param rawPassword 明文密码
 */
public record LoginCommand(String username, String rawPassword) {
}
