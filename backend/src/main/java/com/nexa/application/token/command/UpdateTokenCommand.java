package com.nexa.application.token.command;

/**
 * 更新令牌命令（应用层入参 DTO，F-3006，含 status_only 分支）。
 *
 * <p>接口层 PUT 请求翻译为本命令。{@code actorUserId} 取自认证主体（self-scope 校验依据）。
 * {@code statusOnly=true} 时只切换 {@code status}（启用/禁用），其余覆盖式字段一律忽略——避免脱敏
 * 回显的占位字段被误回写（F-3006 status_only 语义）。{@code endpointLimits} 承载端点级减法约束
 * （F-3012）。</p>
 *
 * @param actorUserId        操作者用户 id（self-scope 校验，必 > 0）
 * @param id                 目标令牌 id（必填）
 * @param statusOnly         是否仅更新状态（true→只改 status）
 * @param status             目标状态码（status_only=true 时用，1=启用 2=禁用）
 * @param name               新令牌名（覆盖式分支必填，≤50）
 * @param remainQuota        新剩余配额
 * @param unlimitedQuota     新无限额度开关（可空→false）
 * @param expiredTime        新过期时间（可空→-1）
 * @param modelLimitsEnabled 新模型限制开关（可空→false）
 * @param modelLimits        新允许模型 JSON 串（可空）
 * @param allowIps           新 IP 白名单（可空→空串）
 * @param group              新调用分组（可空→空串）
 * @param crossGroupRetry    新跨组重试开关（可空→false）
 * @param endpointLimits     新端点级减法约束 JSON 串（可空→空串；非空即启用）
 */
public record UpdateTokenCommand(
        long actorUserId,
        long id,
        boolean statusOnly,
        Integer status,
        String name,
        Long remainQuota,
        Boolean unlimitedQuota,
        Long expiredTime,
        Boolean modelLimitsEnabled,
        String modelLimits,
        String allowIps,
        String group,
        Boolean crossGroupRetry,
        String endpointLimits) {
}
