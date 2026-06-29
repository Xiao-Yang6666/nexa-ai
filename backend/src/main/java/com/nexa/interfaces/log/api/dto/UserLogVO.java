package com.nexa.interfaces.log.api.dto;

import com.nexa.domain.log.model.LogEntry;

/**
 * 用户视图日志 DTO（序列化层裁剪，**B / 成本 / 利润 / 渠道 / 上游请求 ID 绝不出现**）。
 *
 * <p>可见性铁律（COMPAT-LAYER-DATA-OBJECTS §3.1 + ADR-COMPAT-05 序列化层闸，三道闸之一）：
 * UserAuth 日志接口（F-4002 自助列表 / F-4003 按令牌）只返 C/A + quota(=quota_sell 售价客户可见) +
 * 协议三字段 + token/model/group/ip/ua 等本人可见信息，<b>结构级剔除</b>
 * {@code actual_upstream_model}(B)、{@code quota_cost}、{@code quota_profit}、{@code channel}/{@code channel_name}、
 * {@code upstream_request_id}、{@code other} 内部 JSON。由 {@link #from(LogEntry)} 静态裁剪，不靠前端隐藏。</p>
 *
 * <p>对齐 openapi components.schemas.UserLogVO。{@code quota} 取 quota_sell 口径（=客户实付），
 * {@code modelName/requestedModel} 均为 C（兼容现网两个字段）。</p>
 *
 * @param id                日志 id
 * @param createdAt         创建时间（epoch 秒）
 * @param type              日志类型编码（0~7）
 * @param content           内容（错误日志为脱敏后文本）
 * @param tokenName         令牌名
 * @param modelName         模型名（=C 客户输入名）
 * @param requestedModel    C 客户输入名
 * @param resolvedPublicModel A 平台公开名
 * @param group             分组
 * @param promptTokens      入参 token
 * @param completionTokens  出参 token
 * @param quota             实付额度（=quota_sell，客户可见）
 * @param useTime           耗时 ms
 * @param isStream          是否流式
 * @param ip                客户端 IP
 * @param userAgent         UA
 * @param requestId         请求 id
 * @param inboundProtocol   入站协议
 * @param upstreamProtocol  上游协议
 * @param protocolConverted 是否发生协议转换
 */
public record UserLogVO(
        Long id,
        long createdAt,
        int type,
        String content,
        String tokenName,
        String modelName,
        String requestedModel,
        String resolvedPublicModel,
        String group,
        int promptTokens,
        int completionTokens,
        long quota,
        int useTime,
        boolean isStream,
        String ip,
        String userAgent,
        String requestId,
        String inboundProtocol,
        String upstreamProtocol,
        boolean protocolConverted
) {

    /**
     * 从领域日志裁剪为用户视图（B/成本/利润/渠道/上游请求 ID/Other 全部丢弃）。
     *
     * @param log 领域日志对象
     * @return 用户视图 DTO
     */
    public static UserLogVO from(LogEntry log) {
        return new UserLogVO(
                log.id(),
                log.createdAt(),
                log.type() == null ? 0 : log.type().code(),
                log.content(),
                log.tokenName(),
                log.modelName(),
                log.requestedModel(),
                log.resolvedPublicModel(),
                log.group(),
                log.promptTokens(),
                log.completionTokens(),
                log.quotaSell(),          // 客户视图额度用售价口径，绝不暴露 cost/profit
                log.useTime(),
                log.isStream(),
                log.ip(),
                log.userAgent(),
                log.requestId(),
                log.inboundProtocol(),
                log.upstreamProtocol(),
                log.isProtocolConverted());
    }
}
