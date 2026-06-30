package com.nexa.interfaces.api.token.dto;

import com.nexa.domain.token.model.Token;

/**
 * 令牌客户视图 DTO（接口层出参，对齐 openapi TokenUserVO，F-3002/F-3003/F-3001/F-3006）。
 *
 * <p>key 字段经 {@link Token#maskedKey()} 脱敏（MaskTokenKey）；绝不含成本/供应商/上游字段。
 * 列表/搜索/创建/更新场景的默认出参视图。仅 F-3004/F-3005 受控端点才下发完整明文 key。</p>
 *
 * @param id                 令牌 id
 * @param name               令牌名
 * @param key                脱敏 key（MaskTokenKey）
 * @param status             状态码（1=启用，2=禁用）
 * @param remainQuota        剩余配额
 * @param unlimitedQuota     是否无限额度
 * @param usedQuota          已用配额
 * @param expiredTime        过期时间 epoch 秒（-1=永不过期）
 * @param group              调用分组
 * @param modelLimitsEnabled 是否启用模型限制
 * @param modelLimits        允许模型 JSON 串（减法约束）
 * @param allowIps           IP 白名单
 * @param crossGroupRetry    跨组重试开关
 * @param accessedTime       最近访问时间 epoch 秒（可空）
 * @param createdTime        创建时间 epoch 秒
 */
public record TokenUserVO(
        Long id,
        String name,
        String key,
        int status,
        long remainQuota,
        boolean unlimitedQuota,
        long usedQuota,
        long expiredTime,
        String group,
        boolean modelLimitsEnabled,
        String modelLimits,
        String allowIps,
        boolean crossGroupRetry,
        Long accessedTime,
        Long createdTime) {

    /**
     * 从领域聚合映射为客户视图（key 脱敏）。
     *
     * @param token 令牌聚合
     * @return 客户视图 DTO
     */
    public static TokenUserVO from(Token token) {
        return new TokenUserVO(
                token.id(),
                token.name(),
                token.maskedKey(),
                token.status().code(),
                token.remainQuota(),
                token.unlimitedQuota(),
                token.usedQuota(),
                token.expiredTime(),
                token.group(),
                token.modelLimitsEnabled(),
                token.modelLimits(),
                token.allowIps(),
                token.crossGroupRetry(),
                token.accessedTime(),
                token.createdTime());
    }
}
