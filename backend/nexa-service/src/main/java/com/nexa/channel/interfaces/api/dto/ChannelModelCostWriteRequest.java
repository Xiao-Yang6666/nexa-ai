package com.nexa.channel.interfaces.api.dto;

import java.math.BigDecimal;

/** F-6006 创建/更新成本倍率请求（对齐 openapi ChannelModelCostWriteRequest；upsert 语义）。 */
public record ChannelModelCostWriteRequest(
        Integer channelId,
        String upstreamModel,
        BigDecimal costRatio,
        BigDecimal completionCostRatio,
        Boolean enabled,
        String remark
) {}
