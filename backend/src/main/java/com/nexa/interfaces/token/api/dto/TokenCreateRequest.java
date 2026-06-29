package com.nexa.interfaces.token.api.dto;

import com.nexa.application.token.CreateTokenCommand;

/**
 * 创建令牌请求 DTO（接口层入参，对齐 openapi TokenCreateRequest，F-3001）。
 *
 * <p>接口层绑定入参后翻译为 {@link CreateTokenCommand}，归属用户从 {@code @CurrentActor} 取而非请求体。
 * 字段校验由领域工厂 {@link com.nexa.domain.token.model.Token#create} 执行（backend-engineer §2.2 充血）。</p>
 *
 * @param name               令牌名（required，≤50）
 * @param remain_quota       剩余配额（unlimited_quota=false 时校验范围）
 * @param unlimited_quota    是否无限额度（可空→false）
 * @param expired_time       过期时间 epoch 秒（可空→-1 永不过期）
 * @param model_limits_enabled 是否启用模型限制（可空→false）
 * @param model_limits       允许模型 JSON 串（可空）
 * @param allow_ips          IP 白名单（可空→空串）
 * @param group              调用分组（可空→空串）
 * @param cross_group_retry  跨组重试开关（可空→false）
 */
public record TokenCreateRequest(
        String name,
        Long remain_quota,
        Boolean unlimited_quota,
        Long expired_time,
        Boolean model_limits_enabled,
        String model_limits,
        String allow_ips,
        String group,
        Boolean cross_group_retry) {

    /**
     * 翻译为应用层命令（归属用户由控制器从 @CurrentActor 注入）。
     *
     * @param actorUserId 认证主体用户 id
     * @return 创建命令
     */
    public CreateTokenCommand toCommand(long actorUserId) {
        return new CreateTokenCommand(
                actorUserId,
                name,
                remain_quota,
                unlimited_quota,
                expired_time,
                model_limits_enabled,
                model_limits,
                allow_ips,
                group,
                cross_group_retry);
    }
}
