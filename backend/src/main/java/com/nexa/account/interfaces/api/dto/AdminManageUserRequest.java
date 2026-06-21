package com.nexa.account.interfaces.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 管理端用户状态管理请求 DTO（接口层入参，对齐 openapi.yaml {@code POST /api/user/manage}，F-1010）。
 *
 * <p>请求 schema：{@code id} + {@code action}（{@code disable}/{@code enable}/{@code promote}/
 * {@code demote}/{@code delete}）均必填。action 的合法性与角色越权护栏是领域/应用规则
 * （{@code ManageUserUseCase} + 聚合根护栏），本 DTO 仅做非空协议校验。</p>
 *
 * @param id     目标用户 id（必填）
 * @param action 管理动作（必填：enable/disable/promote/demote/delete）
 */
public record AdminManageUserRequest(
        @NotNull(message = "id must not be null")
        Long id,

        @NotBlank(message = "action must not be blank")
        String action) {
}
