package com.nexa.interfaces.api.token.dto;

import com.nexa.application.token.UpdateTokenCommand;

/**
 * 更新令牌请求 DTO（接口层入参，对齐 openapi PUT /api/token/ body，F-3006 含 status_only）。
 *
 * <p>接口层绑定入参后翻译为 {@link UpdateTokenCommand}。{@code status_only=true} 时只传 status，
 * 其余覆盖式字段一律忽略——避免脱敏回显的占位字段被误回写。{@code endpoint_limits} 承载端点级减法约束
 * （F-3012）。</p>
 *
 * @param id                   目标令牌 id（required）
 * @param status_only          是否仅更新状态（可空→false）
 * @param status               目标状态码（status_only=true 时用，1/2）
 * @param name                 新令牌名（覆盖式分支 required，≤50）
 * @param remain_quota         新剩余配额
 * @param unlimited_quota      新无限额度开关（可空→false）
 * @param expired_time         新过期时间（可空→-1）
 * @param model_limits_enabled 新模型限制开关（可空→false）
 * @param model_limits         新允许模型 JSON 串（可空）
 * @param allow_ips            新 IP 白名单（可空→空串）
 * @param group                新调用分组（可空→空串）
 * @param cross_group_retry    新跨组重试开关（可空→false）
 * @param endpoint_limits      新端点级减法约束 JSON 串（可空→空串；非空即启用）
 */
public record TokenUpdateRequest(
        Long id,
        Boolean status_only,
        Integer status,
        String name,
        Long remain_quota,
        Boolean unlimited_quota,
        Long expired_time,
        Boolean model_limits_enabled,
        String model_limits,
        String allow_ips,
        String group,
        Boolean cross_group_retry,
        String endpoint_limits) {

    /**
     * 翻译为应用层命令（操作者由控制器从 @CurrentActor 注入）。
     *
     * @param actorUserId 认证主体用户 id
     * @return 更新命令
     */
    public UpdateTokenCommand toCommand(long actorUserId) {
        return new UpdateTokenCommand(
                actorUserId,
                id == null ? 0L : id,
                Boolean.TRUE.equals(status_only),
                status,
                name,
                remain_quota,
                unlimited_quota,
                expired_time,
                model_limits_enabled,
                model_limits,
                allow_ips,
                group,
                cross_group_retry,
                endpoint_limits);
    }
}
