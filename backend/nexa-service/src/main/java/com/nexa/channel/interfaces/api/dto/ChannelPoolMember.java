package com.nexa.channel.interfaces.api.dto;

import com.nexa.channel.domain.model.Channel;

/**
 * 供应渠道池成员视图（接口层出参，AdminView，F-6005）。
 *
 * <p>对齐 openapi ChannelPoolMember。含 upstream_model=B + channel 维度（channel_id / channel_name /
 * group / priority / weight / status / enabled），仅 admin/root，客户绝不可见（B 不可见序列化层闸）。</p>
 *
 * <p>F-6005 背景：同一 A→B 下的同品质渠道池成员（售价恒定、按 priority 分层 + weight 加权随机选渠）。
 * 品质不同的拆独立 A（COMPAT §3 模型分级），故混渠道时品质已对齐无需再校验。</p>
 */
public record ChannelPoolMember(
        Integer channelId,
        String channelName,
        String upstreamModel,
        String group,
        Long priority,
        Integer weight,
        Integer status,
        Boolean enabled
) {
    /**
     * 由 Channel 聚合投影为渠道池成员视图（upstreamModel 由调用方填充——查询端点按 B 过滤，所有成员共享同一 B）。
     *
     * @param channel       渠道聚合
     * @param upstreamModel 真实模型 B（成员共享的 B 名）
     * @return 渠道池成员视图
     */
    public static ChannelPoolMember from(Channel channel, String upstreamModel) {
        return new ChannelPoolMember(
                channel.id().intValue(),
                channel.name(),
                upstreamModel,
                channel.group(),
                channel.priority(),
                channel.weight(),
                channel.status().code(),
                channel.status().isEnabled()
        );
    }
}
