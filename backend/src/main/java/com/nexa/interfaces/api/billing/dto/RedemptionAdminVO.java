package com.nexa.interfaces.api.billing.dto;

import com.nexa.domain.billing.model.Redemption;

/**
 * 兑换码管理视图（管理端出参，对齐 openapi {@code RedemptionAdminVO}）。
 *
 * <p>管理端可见全字段（含明文 Key、核销人、核销时间）——兑换码本身是管理员生成/分发的凭证，
 * 管理视图展示 Key 供分发，与「客户视图脱敏」无冲突（兑换码无客户列表端点）。</p>
 *
 * @param id          主键
 * @param name        名称/批次标识
 * @param key         兑换码明文
 * @param status      状态编码（1=未使用/2=已使用/3=已禁用）
 * @param quota       面额（quota 单位）
 * @param createdTime 创建时间（epoch 秒）
 * @param redeemedTime 核销时间（epoch 秒，未核销为 null）
 * @param usedUserId  核销人用户 id（未核销为 null）
 * @param expiredTime 过期时间（epoch 秒，0=不过期）
 */
public record RedemptionAdminVO(Long id, String name, String key, int status, long quota,
                                  Long createdTime, Long redeemedTime, Integer usedUserId, Long expiredTime) {

    /**
     * 由领域聚合投影为管理视图。
     *
     * @param r 兑换码聚合
     * @return 管理视图 DTO
     */
    public static RedemptionAdminVO from(Redemption r) {
        return new RedemptionAdminVO(
                r.id(), r.name(), r.key(), r.status().code(), r.quota().value(),
                r.createdTime(), r.redeemedTime(), r.usedUserId(), r.expiredTime());
    }
}
