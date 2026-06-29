package com.nexa.application.account;

/**
 * 管理端创建用户命令（应用层入参 DTO，F-1009）。
 *
 * <p>对齐 API-ENDPOINTS §1.4 {@code POST /api/user/}：{@code username}/{@code password} 必填，
 * {@code displayName} 可选，{@code roleCode} 为目标用户角色（领域护栏要求严格低于操作者角色）。
 * {@code operatorRoleCode} 是发起创建的管理员角色，用于越权创建护栏（AC-10）。</p>
 *
 * @param username         用户名（必填）
 * @param rawPassword      明文密码（必填）
 * @param email            邮箱（可选，可为 null/空白）
 * @param displayName      展示名（可选）
 * @param roleCode         目标用户角色编码（1/10/100）
 * @param operatorRoleCode 操作者角色编码
 */
public record CreateUserByAdminCommand(String username,
                                       String rawPassword,
                                       String email,
                                       String displayName,
                                       int roleCode,
                                       int operatorRoleCode) {
}
