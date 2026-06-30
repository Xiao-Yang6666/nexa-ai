package com.nexa.infrastructure.relay.persistence.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.relay.model.RelayLog;
import com.nexa.infrastructure.persistence.JsonbStringTypeHandler;

/**
 * Relay 用量日志 JPA 持久化实体（基础设施层，对齐 V11 {@code logs} 与 DB-SCHEMA §5 + 10 新列）。
 *
 * <p>与领域聚合 {@link com.nexa.domain.relay.model.RelayLog} 分离。{@code other} 用 Hibernate 6
 * {@code @JdbcTypeCode(SqlTypes.JSON)} 以 String 承载 JSONB；{@code group} 为 PG 保留字双引号转义；
 * {@code channel_name} 为只读 join 列（insertable/updatable=false）。{@code actual_upstream_model}(B) /
 * {@code quota_cost} / {@code quota_profit} 落库但客户视图 DTO 裁剪掉（可见性铁律）。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：本 PO 同时保留 JPA 注解（{@code @Entity/@Table/@Column/@Id/@JdbcTypeCode}，
 * 满足并存期 {@code ddl-auto=validate} 全局启动校验）与 MyBatis-Plus 注解
 * （{@code @TableName/@TableId/@TableField}，供 Mapper 实际读写）。{@code other}(jsonb) 在 MyBatis-Plus 侧
 * 由 {@link JsonbStringTypeHandler} 承载（故 {@code @TableName} 开 {@code autoResultMap=true} 使读取也走该 handler）；
 * {@code channel_name} 只读列以 {@code @TableField(insertStrategy/updateStrategy=NEVER)} 对齐 JPA 的
 * insertable/updatable=false。两套注解命名空间独立、互不读取。阶段4 统一移除 JPA 注解。</p>
 */
@TableName(value = "logs", autoResultMap = true)
public class LogPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Integer userId;

    @TableField("created_at")
    private Long createdAt;

    @TableField("type")
    private Integer type;

    @TableField("content")
    private String content;

    @TableField("username")
    private String username;

    @TableField("token_name")
    private String tokenName;

    @TableField("model_name")
    private String modelName;

    @TableField("quota")
    private Integer quota;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("use_time")
    private Integer useTime;

    @TableField("is_stream")
    private Boolean isStream;

    @TableField("channel")
    private Integer channelId;

    @TableField(value = "channel_name", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String channelName;

    @TableField("token_id")
    private Integer tokenId;

    @TableField("\"group\"")
    private String group;

    @TableField("ip")
    private String ip;

    @TableField("request_id")
    private String requestId;

    @TableField("upstream_request_id")
    private String upstreamRequestId;

    @TableField(value = "other", typeHandler = JsonbStringTypeHandler.class)
    private String other;

    @TableField("requested_model")
    private String requestedModel;

    @TableField("resolved_public_model")
    private String resolvedPublicModel;

    @TableField("actual_upstream_model")
    private String actualUpstreamModel;

    @TableField("inbound_protocol")
    private String inboundProtocol;

    @TableField("upstream_protocol")
    private String upstreamProtocol;

    @TableField("protocol_converted")
    private Boolean protocolConverted;

    @TableField("user_agent")
    private String userAgent;

    @TableField("quota_sell")
    private Integer quotaSell;

    @TableField("quota_cost")
    private Integer quotaCost;

    @TableField("quota_profit")
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

    // ---- 就近映射工厂方法：把 RepositoryImpl 里的 LogPO 装配 + Long→int 窄化逻辑搬到这里 ----

    /**
     * 领域日志 → PO（持久化方向，write-only）：逐字段映射，{@code type} 取枚举 code（null→0），
     * {@code userId}/{@code channelId}/{@code tokenId} 由领域 Long 窄化为表 int 列（值域受控，
     * relay 日志不会超 int 范围），{@code channelName} 为只读 join 列不写入。无 toDomain（本 BC 只写）。
     *
     * @param log 领域日志对象（非空）
     * @return 待持久化的 PO
     */
    public static LogPO of(RelayLog log) {
        LogPO e = new LogPO();
        e.setUserId(toInt(log.userId()));
        e.setCreatedAt(log.createdAt());
        e.setType(log.type() == null ? 0 : log.type().code());
        e.setContent(log.content());
        e.setUsername(log.username());
        e.setTokenName(log.tokenName());
        e.setModelName(log.modelName());
        e.setQuota(log.quota());
        e.setPromptTokens(log.promptTokens());
        e.setCompletionTokens(log.completionTokens());
        e.setUseTime(log.useTime());
        e.setIsStream(log.isStream());
        e.setChannelId(toInt(log.channelId()));
        e.setTokenId(toInt(log.tokenId()));
        e.setGroup(log.group());
        e.setIp(log.ip());
        e.setRequestId(log.requestId());
        e.setUpstreamRequestId(log.upstreamRequestId());
        e.setOther(log.other());
        e.setRequestedModel(log.requestedModel());
        e.setResolvedPublicModel(log.resolvedPublicModel());
        e.setActualUpstreamModel(log.actualUpstreamModel());
        e.setInboundProtocol(log.inboundProtocol());
        e.setUpstreamProtocol(log.upstreamProtocol());
        e.setProtocolConverted(log.isProtocolConverted());
        e.setUserAgent(log.userAgent());
        e.setQuotaSell(log.quotaSell());
        e.setQuotaCost(log.quotaCost());
        e.setQuotaProfit(log.quotaProfit());
        return e;
    }

    /** 领域 Long → 表 int 列窄化（null 透传）。relay 日志值域受控，不会超 int 范围。 */
    private static Integer toInt(Long v) {
        return v == null ? null : v.intValue();
    }
}
