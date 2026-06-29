package com.nexa.interfaces.account.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理端创建用户请求 DTO（接口层入参，对齐 openapi.yaml {@code POST /api/user/}，F-1009）。
 *
 * <p>请求 schema：{@code username}/{@code password} 必填，{@code display_name}/{@code role} 可选。
 * {@code role} 描述「不可高于自身角色」——该越权护栏是<b>领域规则</b>，在聚合根
 * {@code User.createByAdmin} 内守护，本 DTO 仅做协议级长度/非空校验。JSON 字段 {@code display_name}
 * 经全局 Jackson {@code SNAKE_CASE} 映射到 {@code displayName}。</p>
 *
 * <p>{@code role} 为 {@code Integer} 包装类型：缺省（null）时由控制器回落为 common（1）默认角色，
 * 避免 int 默认 0 被 {@code Role.fromCode} 当作未知编码拒绝。</p>
 *
 * @param username    用户名（必填，≤20）
 * @param password    明文密码（必填，8~20）
 * @param displayName 展示名（可选，≤20）
 * @param role        目标用户角色编码（可选，缺省 common=1；护栏要求严格低于操作者）
 */
public record AdminCreateUserRequest(
        @NotBlank(message = "username must not be blank")
        @Size(max = 20, message = "username length must be <= 20")
        String username,

        @NotBlank(message = "password must not be blank")
        @Size(min = 8, max = 20, message = "password length must be between 8 and 20")
        String password,

        @Size(max = 20, message = "display_name length must be <= 20")
        String displayName,

        Integer role) {
}
