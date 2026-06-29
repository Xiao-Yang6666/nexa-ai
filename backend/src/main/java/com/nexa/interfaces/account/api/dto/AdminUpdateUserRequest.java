package com.nexa.interfaces.account.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 管理端更新用户资料请求 DTO（接口层入参，对齐 openapi.yaml {@code PUT /api/user/}，F-1011/1013/1014）。
 *
 * <p>请求 schema：{@code id} 必填；{@code display_name}/{@code email}/{@code group}/{@code quota}/
 * {@code remark}/{@code status} 可选，遵循<b>部分更新</b>语义（缺省=不改）。各字段的长度/格式/护栏校验
 * 是领域规则，在聚合根 {@code User.updateProfileByAdmin} 内守护；本 DTO 仅承载协议级入参。JSON
 * 字段经全局 Jackson {@code SNAKE_CASE} 映射（{@code display_name}→{@code displayName}）。</p>
 *
 * @param id          目标用户 id（必填）
 * @param displayName 新展示名（null=不改；空白=清空）
 * @param email       新邮箱（null=不改）
 * @param group       新分组（null=不改）
 * @param quota       新额度（null=不改；Long 包装区别于「显式设 0」）
 * @param remark      新备注（null=不改；空白=清空）
 * @param status      新状态编码（null=不改；1=启用，其它=禁用）
 * @param discountRatio 新用户专属折扣（null=不改；售价侧，在分组倍率之后再乘，须 ≥0）
 */
public record AdminUpdateUserRequest(
        @NotNull(message = "id must not be null")
        Long id,

        String displayName,
        String email,
        String group,
        Long quota,
        String remark,
        Integer status,
        java.math.BigDecimal discountRatio) {
}
