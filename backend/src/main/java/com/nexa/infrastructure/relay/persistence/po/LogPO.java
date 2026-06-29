package com.nexa.infrastructure.relay.persistence.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Relay 用量日志 JPA 持久化实体（基础设施层，对齐 V11 {@code logs} 与 DB-SCHEMA §5 + 10 新列）。
 *
 * <p>与领域聚合 {@link com.nexa.domain.relay.model.RelayLog} 分离。{@code other} 用 Hibernate 6
 * {@code @JdbcTypeCode(SqlTypes.JSON)} 以 String 承载 JSONB；{@code group} 为 PG 保留字双引号转义；
 * {@code channel_name} 为只读 join 列（insertable/updatable=false）。{@code actual_upstream_model}(B) /
 * {@code quota_cost} / {@code quota_profit} 落库但客户视图 DTO 裁剪掉（可见性铁律）。</p>
 */
@Entity
@Table(name = "logs", indexes = {
        @Index(name = "idx_created_at_id", columnList = "created_at, id"),
        @Index(name = "idx_user_id_id", columnList = "user_id, id"),
        @Index(name = "idx_logs_username", columnList = "username"),
        @Index(name = "idx_logs_token_name", columnList = "token_name"),
        @Index(name = "idx_logs_model_name", columnList = "model_name"),
        @Index(name = "idx_logs_channel", columnList = "channel"),
        @Index(name = "idx_logs_token_id", columnList = "token_id"),
        @Index(name = "idx_logs_ip", columnList = "ip"),
        @Index(name = "idx_logs_requested_model", columnList = "requested_model"),
        @Index(name = "idx_logs_resolved_public_model", columnList = "resolved_public_model"),
        @Index(name = "idx_logs_actual_upstream_model", columnList = "actual_upstream_model")
})
public class LogPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "type", nullable = false)
    private Integer type;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "username")
    private String username;

    @Column(name = "token_name")
    private String tokenName;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "quota")
    private Integer quota;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "use_time")
    private Integer useTime;

    @Column(name = "is_stream")
    private Boolean isStream;

    @Column(name = "channel")
    private Integer channelId;

    @Column(name = "channel_name", insertable = false, updatable = false)
    private String channelName;

    @Column(name = "token_id")
    private Integer tokenId;

    @Column(name = "\"group\"")
    private String group;

    @Column(name = "ip")
    private String ip;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "upstream_request_id", length = 128)
    private String upstreamRequestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "other", columnDefinition = "jsonb")
    private String other;

    @Column(name = "requested_model")
    private String requestedModel;

    @Column(name = "resolved_public_model")
    private String resolvedPublicModel;

    @Column(name = "actual_upstream_model")
    private String actualUpstreamModel;

    @Column(name = "inbound_protocol", length = 32)
    private String inboundProtocol;

    @Column(name = "upstream_protocol", length = 32)
    private String upstreamProtocol;

    @Column(name = "protocol_converted")
    private Boolean protocolConverted;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "quota_sell")
    private Integer quotaSell;

    @Column(name = "quota_cost")
    private Integer quotaCost;

    @Column(name = "quota_profit")
    private Integer quotaProfit;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getTokenName() { return tokenName; }
    public void setTokenName(String tokenName) { this.tokenName = tokenName; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Integer getQuota() { return quota; }
    public void setQuota(Integer quota) { this.quota = quota; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    public Integer getUseTime() { return useTime; }
    public void setUseTime(Integer useTime) { this.useTime = useTime; }
    public Boolean getIsStream() { return isStream; }
    public void setIsStream(Boolean isStream) { this.isStream = isStream; }
    public Integer getChannelId() { return channelId; }
    public void setChannelId(Integer channelId) { this.channelId = channelId; }
    public String getChannelName() { return channelName; }
    public Integer getTokenId() { return tokenId; }
    public void setTokenId(Integer tokenId) { this.tokenId = tokenId; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getUpstreamRequestId() { return upstreamRequestId; }
    public void setUpstreamRequestId(String upstreamRequestId) { this.upstreamRequestId = upstreamRequestId; }
    public String getOther() { return other; }
    public void setOther(String other) { this.other = other; }
    public String getRequestedModel() { return requestedModel; }
    public void setRequestedModel(String requestedModel) { this.requestedModel = requestedModel; }
    public String getResolvedPublicModel() { return resolvedPublicModel; }
    public void setResolvedPublicModel(String resolvedPublicModel) { this.resolvedPublicModel = resolvedPublicModel; }
    public String getActualUpstreamModel() { return actualUpstreamModel; }
    public void setActualUpstreamModel(String actualUpstreamModel) { this.actualUpstreamModel = actualUpstreamModel; }
    public String getInboundProtocol() { return inboundProtocol; }
    public void setInboundProtocol(String inboundProtocol) { this.inboundProtocol = inboundProtocol; }
    public String getUpstreamProtocol() { return upstreamProtocol; }
    public void setUpstreamProtocol(String upstreamProtocol) { this.upstreamProtocol = upstreamProtocol; }
    public Boolean getProtocolConverted() { return protocolConverted; }
    public void setProtocolConverted(Boolean protocolConverted) { this.protocolConverted = protocolConverted; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public Integer getQuotaSell() { return quotaSell; }
    public void setQuotaSell(Integer quotaSell) { this.quotaSell = quotaSell; }
    public Integer getQuotaCost() { return quotaCost; }
    public void setQuotaCost(Integer quotaCost) { this.quotaCost = quotaCost; }
    public Integer getQuotaProfit() { return quotaProfit; }
    public void setQuotaProfit(Integer quotaProfit) { this.quotaProfit = quotaProfit; }
}
