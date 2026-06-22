package com.nexa.relay.domain.model;

import com.nexa.relay.domain.ir.UsageIR;
import com.nexa.relay.domain.vo.BillingResult;
import com.nexa.relay.domain.vo.LogType;
import com.nexa.relay.domain.vo.ProtocolFormat;
import com.nexa.relay.domain.vo.RelayInfo;

/**
 * Relay 用量日志聚合根（RL-1/RL-3/RL-7 落 Log，充血模型）。
 *
 * <p>领域规则来源：prd-relay RL-7 第⑨步 + DB-SCHEMA §5。一条 Type=2(Consume) 的 Log 同时含：
 * <ul>
 *   <li>三段模型 C/A/B（{@code requestedModel}/{@code resolvedPublicModel}/{@code actualUpstreamModel}）；</li>
 *   <li>实际供应商 channel + 协议三字段（inbound/upstream/protocol_converted）；</li>
 *   <li>双价记账 quota_sell/quota_cost/quota_profit。</li>
 * </ul>
 * 错误日志为 Type=5(Error)（RL-3 re_log，content 经脱敏后写）。</p>
 *
 * <p>{@code modelName} 取值 = {@code requestedModel}(C)，保留现网报表语义（DB-SCHEMA §5 口径）。
 * 零框架依赖，与 {@code LogJpaEntity} 分离。</p>
 */
public class RelayLog {

    private Long id;
    private Long userId;
    private long createdAt;
    private LogType type;
    private String content;
    private String username;
    private String tokenName;
    private String modelName;          // = requestedModel(C)
    private int quota;                 // = quotaSell 口径
    private int promptTokens;
    private int completionTokens;
    private int useTime;
    private boolean stream;
    private Long channelId;
    private Long tokenId;
    private String group;
    private String ip;
    private String requestId;
    private String upstreamRequestId;
    private String other;              // 附加 JSON（含 cost_missing/channel_redirect 诊断）

    // 三段模型 + 协议 + UA + 双价
    private String requestedModel;     // C
    private String resolvedPublicModel;// A
    private String actualUpstreamModel;// B (仅 admin/root)
    private String inboundProtocol;
    private String upstreamProtocol;
    private boolean protocolConverted;
    private String userAgent;
    private int quotaSell;
    private int quotaCost;             // 仅 admin/root
    private int quotaProfit;           // 仅 admin/root

    private RelayLog() {
    }

    /**
     * 从 RelayInfo + usage + billing 构造一条消费日志（RL-7 第⑨步，Type=2 Consume）。
     *
     * @param info     单请求中继上下文（已填充三段模型/协议/渠道/分组）
     * @param usage    IR token 用量
     * @param billing  双价记账结果
     * @param nowEpoch 当前 epoch 秒
     * @param other    附加 JSON（cost_missing / channel_redirect 等诊断；可空）
     * @return 消费日志聚合
     */
    public static RelayLog consume(RelayInfo info, UsageIR usage, BillingResult billing, long nowEpoch, String other) {
        RelayLog log = new RelayLog();
        log.type = LogType.CONSUME;
        log.createdAt = nowEpoch;
        log.userId = info.userId();
        log.username = info.username();
        log.tokenName = info.tokenName();
        log.tokenId = info.tokenId();
        log.channelId = info.channelId();
        log.group = info.usingGroup();
        log.ip = info.ip();
        log.requestId = info.requestId();
        log.upstreamRequestId = info.upstreamRequestId();
        log.userAgent = info.userAgent();
        log.stream = info.isStream();
        log.useTime = info.useTimeMs();
        log.promptTokens = usage.promptTokens();
        log.completionTokens = usage.completionTokens();

        // 三段模型；model_name = C 保留现网语义
        log.requestedModel = info.requestedModel();
        log.resolvedPublicModel = info.resolvedPublicModel();
        log.actualUpstreamModel = info.upstreamModelName();
        log.modelName = info.requestedModel();

        // 协议
        ProtocolFormat in = info.inboundFormat();
        ProtocolFormat up = info.targetProtocol();
        log.inboundProtocol = in == null ? "" : in.wireValue();
        log.upstreamProtocol = up == null ? "" : up.wireValue();
        log.protocolConverted = !info.isPassthrough();

        // 双价；quota(现网) = quotaSell 口径
        log.quotaSell = billing.quotaSell();
        log.quotaCost = billing.quotaCost();
        log.quotaProfit = billing.quotaProfit();
        log.quota = billing.quotaSell();

        log.other = other;
        return log;
    }

    /**
     * 构造一条错误日志（RL-3 re_log，Type=5 Error）。
     *
     * @param info             中继上下文（取 channel/model）
     * @param maskedContent    脱敏后的错误内容（MaskSensitiveErrorWithStatusCode）
     * @param upstreamStatus   上游状态码
     * @param nowEpoch         当前 epoch 秒
     * @return 错误日志聚合
     */
    public static RelayLog error(RelayInfo info, String maskedContent, int upstreamStatus, long nowEpoch) {
        RelayLog log = new RelayLog();
        log.type = LogType.ERROR;
        log.createdAt = nowEpoch;
        log.userId = info.userId();
        log.username = info.username();
        log.tokenName = info.tokenName() == null ? "" : info.tokenName();
        log.tokenId = info.tokenId();
        log.channelId = info.channelId();
        log.modelName = info.requestedModel();
        log.requestedModel = info.requestedModel();
        log.resolvedPublicModel = info.resolvedPublicModel();
        log.actualUpstreamModel = info.upstreamModelName();
        log.content = maskedContent;
        log.other = "{\"status_code\":" + upstreamStatus + "}";
        log.group = info.usingGroup();
        log.ip = info.ip() == null ? "" : info.ip();
        log.userAgent = info.userAgent() == null ? "" : info.userAgent();
        // 错误场景无成功 usage/计费：tokens/金额/耗时记 0；协议字段按入站/目标格式落（NOT NULL 兜空串）。
        log.promptTokens = 0;
        log.completionTokens = 0;
        log.useTime = 0;
        log.quota = 0;
        log.quotaSell = 0;
        log.quotaCost = 0;
        log.quotaProfit = 0;
        log.inboundProtocol = info.inboundFormat() == null ? "" : info.inboundFormat().wireValue();
        log.upstreamProtocol = info.targetProtocol() == null ? "" : info.targetProtocol().wireValue();
        log.protocolConverted = !info.isPassthrough();
        return log;
    }

    // --- getters（仓储映射用） ---
    public Long id() { return id; }
    public void assignId(Long id) { this.id = id; }
    public Long userId() { return userId; }
    public long createdAt() { return createdAt; }
    public LogType type() { return type; }
    public String content() { return content; }
    public String username() { return username; }
    public String tokenName() { return tokenName; }
    public String modelName() { return modelName; }
    public int quota() { return quota; }
    public int promptTokens() { return promptTokens; }
    public int completionTokens() { return completionTokens; }
    public int useTime() { return useTime; }
    public boolean isStream() { return stream; }
    public Long channelId() { return channelId; }
    public Long tokenId() { return tokenId; }
    public String group() { return group; }
    public String ip() { return ip; }
    public String requestId() { return requestId; }
    public String upstreamRequestId() { return upstreamRequestId; }
    public String other() { return other; }
    public String requestedModel() { return requestedModel; }
    public String resolvedPublicModel() { return resolvedPublicModel; }
    public String actualUpstreamModel() { return actualUpstreamModel; }
    public String inboundProtocol() { return inboundProtocol; }
    public String upstreamProtocol() { return upstreamProtocol; }
    public boolean isProtocolConverted() { return protocolConverted; }
    public String userAgent() { return userAgent; }
    public int quotaSell() { return quotaSell; }
    public int quotaCost() { return quotaCost; }
    public int quotaProfit() { return quotaProfit; }
}
