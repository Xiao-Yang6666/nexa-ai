package com.nexa.interfaces.relay.api.dto;

import com.nexa.domain.relay.model.RelayLog;

/**
 * 管理视图日志 DTO（全字段含 B + 成本 + 利润 + 协议三字段，COMPAT-LAYER-DATA-OBJECTS §3.1）。
 *
 * <p>仅 AdminAuth/RootAuth 接口使用：给全链 C→A→B + 协议 + 售价/成本/利润 + channel_id。
 * 与 {@link UserLogView} 的差异即「客户看不到 B」三道闸之序列化层。</p>
 */
public record AdminLogView(
        String requestedModel,
        String resolvedPublicModel,
        String actualUpstreamModel,
        Long channelId,
        String inboundProtocol,
        String upstreamProtocol,
        boolean protocolConverted,
        int promptTokens,
        int completionTokens,
        int quotaSell,
        int quotaCost,
        int quotaProfit,
        long createdAt
) {

    public static AdminLogView from(RelayLog log) {
        return new AdminLogView(
                log.requestedModel(),
                log.resolvedPublicModel(),
                log.actualUpstreamModel(),
                log.channelId(),
                log.inboundProtocol(),
                log.upstreamProtocol(),
                log.isProtocolConverted(),
                log.promptTokens(),
                log.completionTokens(),
                log.quotaSell(),
                log.quotaCost(),
                log.quotaProfit(),
                log.createdAt());
    }
}
