package com.nexa.token.application;

/**
 * 创建令牌命令（应用层入参 DTO，F-3001）。
 *
 * <p>接口层 {@code TokenCreateRequest} 翻译为本命令传入 {@link CreateTokenUseCase}。
 * {@code actorUserId} 取自认证主体（不从请求体读，杜绝伪造他人归属）。其余字段对齐 openapi
 * TokenCreateRequest，由领域工厂 {@code Token.create} 校验/归一。</p>
 *
 * @param actorUserId        归属用户 id（取自 @CurrentActor，必 > 0）
 * @param name               令牌名（必填，≤50）
 * @param remainQuota        剩余配额（unlimited=false 时校验范围）
 * @param unlimitedQuota     是否无限额度（可空→false）
 * @param expiredTime        过期时间 epoch 秒（可空→-1 永不过期）
 * @param modelLimitsEnabled 是否启用模型限制（可空→false）
 * @param modelLimits        允许模型 JSON 串（可空，减法约束）
 * @param allowIps           IP 白名单（可空→空串，按换行切分）
 * @param group              调用分组（可空→空串）
 * @param crossGroupRetry    跨组重试开关（可空→false）
 */
public record CreateTokenCommand(
        long actorUserId,
        String name,
        Long remainQuota,
        Boolean unlimitedQuota,
        Long expiredTime,
        Boolean modelLimitsEnabled,
        String modelLimits,
        String allowIps,
        String group,
        Boolean crossGroupRetry) {
}
