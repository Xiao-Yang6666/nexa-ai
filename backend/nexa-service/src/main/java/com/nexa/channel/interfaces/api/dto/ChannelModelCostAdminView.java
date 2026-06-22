package com.nexa.channel.interfaces.api.dto;

import com.nexa.channel.domain.model.ChannelModelCost;

import java.math.BigDecimal;

/**
 * 供应商成本倍率管理视图（接口层出参，AdminView，F-6006）。
 *
 * <p>对齐 openapi ChannelModelCostAdminView。含 cost_ratio / upstream_model=B，
 * 仅 admin/root，客户绝不可见（B 不可见序列化层闸）。</p>
 */
public record ChannelModelCostAdminView(
        Long id,
        Integer channelId,
        String upstreamModel,
        BigDecimal costRatio,
        BigDecimal completionCostRatio,
        Boolean enabled,
        Long effectiveTime,
        BigDecimal sourceUnitPrice,
        String remark,
        Long createdTime,
        Long updatedTime
) {
    /** 由领域聚合裁剪为管理视图。 */
    public static ChannelModelCostAdminView from(ChannelModelCost c) {
        return new ChannelModelCostAdminView(c.id(), c.channelId(), c.upstreamModel(),
                c.costRatio(), c.completionCostRatio(), c.enabled(), c.effectiveTime(),
                c.sourceUnitPrice(), c.remark(), c.createdTime(), c.updatedTime());
    }
}
