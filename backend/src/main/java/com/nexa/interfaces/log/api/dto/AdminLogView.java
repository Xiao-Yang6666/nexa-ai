package com.nexa.interfaces.log.api.dto;

import com.nexa.domain.log.model.LogEntry;

/**
 * 管理视图日志 DTO（全字段含 B + 成本 + 利润 + 渠道 + 协议三字段 + Other，COMPAT-LAYER-DATA-OBJECTS §3.1）。
 *
 * <p>仅 AdminAuth/RootAuth 接口使用（F-4001 管理端列表）：给全链 C→A→B + 协议 + 售价/成本/利润 +
 * channel/channel_name + upstream_request_id + other。与 {@link UserLogView} 的差异即「客户看不到 B」
 * 三道闸之序列化层——AdminLogView 是<b>唯一</b>下发 B/成本/利润的 DTO，由角色级端点（AdminAuth）守护。</p>
 *
 * <p>对齐 openapi components.schemas.AdminLogView。SNAKE_CASE 全局策略下字段自动转下划线。</p>
 */
public record AdminLogView(
        Long id,
        Long userId,
        String username,
        long createdAt,
        int type,
        String content,
        String tokenName,
        String modelName,
        String requestedModel,
        String resolvedPublicModel,
        String actualUpstreamModel,
        String group,
        int promptTokens,
        int completionTokens,
        long quota,
        int quotaSell,
        int quotaCost,
        int quotaProfit,
        int useTime,
        boolean isStream,
        Long channel,
        String channelName,
        Long tokenId,
        String ip,
        String userAgent,
        String requestId,
        String upstreamRequestId,
        String inboundProtocol,
        String upstreamProtocol,
        boolean protocolConverted,
        String other
) {

    /**
     * 从领域日志构造管理视图（全字段，不裁剪）。
     *
     * @param log 领域日志对象
     * @return 管理视图 DTO
     */
    public static AdminLogView from(LogEntry log) {
        return new AdminLogView(
                log.id(),
                log.userId(),
                log.username(),
                log.createdAt(),
                log.type() == null ? 0 : log.type().code(),
                log.content(),
                log.tokenName(),
                log.modelName(),
                log.requestedModel(),
                log.resolvedPublicModel(),
                log.actualUpstreamModel(),
                log.group(),
                log.promptTokens(),
                log.completionTokens(),
                log.quota(),
                log.quotaSell(),
                log.quotaCost(),
                log.quotaProfit(),
                log.useTime(),
                log.isStream(),
                log.channelId(),
                log.channelName(),
                log.tokenId(),
                log.ip(),
                log.userAgent(),
                log.requestId(),
                log.upstreamRequestId(),
                log.inboundProtocol(),
                log.upstreamProtocol(),
                log.isProtocolConverted(),
                log.other());
    }
}
