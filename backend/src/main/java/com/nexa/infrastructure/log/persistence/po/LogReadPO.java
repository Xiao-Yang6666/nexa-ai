package com.nexa.infrastructure.log.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.log.model.LogEntry;
import com.nexa.domain.log.vo.LogType;

/**
 * 日志读侧 JPA 实体（基础设施层；映射 V11 {@code logs} 表，供日志与用量 BC 查询/审计写/清理）。
 *
 * <p><b>为何独立于 relay 的 {@code LogPO}</b>：DDD 上下文解耦——relay BC 负责写消费/错误日志，
 * 本（log）BC 负责读查询/统计/审计/清理，两个 bounded context 各持自己的持久化实体，不跨 BC 复用
 * （避免 log BC 反向依赖 relay infra）。两实体映射同一张物理表是 JPA 合法用法。</p>
 *
 * <p><b>索引声明归属</b>：物理索引由 V11 Flyway 脚本 + relay {@code LogPO} 的 {@code @Table(indexes=)}
 * 拥有，本读侧实体<b>刻意不再声明</b> {@code @Index}，避免 Hibernate DDL 重复索引定义冲突
 * （生产用 Flyway 管 DDL，实体不参与建表，但保持声明单一来源更稳）。{@code channel_name} 为只读 join 列。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：本 PO 同时保留 JPA 注解（{@code @Entity/@Table/@Column/@Id}，
 * 满足并存期 {@code ddl-auto=validate} 全局启动校验）与 MyBatis-Plus 注解
 * （{@code @TableName/@TableId/@TableField}，供 Mapper 实际读写）。两套注解命名空间独立、互不读取。
 * 阶段4 统一移除 JPA 注解。</p>
 */
@TableName(value = "logs", autoResultMap = true)
public class LogReadPO {

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

    @TableField(value = "channel_name", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
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

    @TableField(value = "other", typeHandler = com.nexa.infrastructure.persistence.JsonbStringTypeHandler.class)
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

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * PO → 领域日志（重建方向）：{@code type} 经 {@link LogType#fromCode} 还原（null→0=UNKNOWN），
     * int 列拓宽为 Long（{@code userId}/{@code channelId}/{@code tokenId} null 保留 null），数值列 null 兜底，
     * 布尔列 null 视为 false。逻辑与原 {@code LogRepositoryImpl#toDomain} 1:1。
     *
     * @return 重建的日志领域对象
     */
    public LogEntry toDomain() {
        return LogEntry.rebuild()
                .id(id)
                .userId(userId == null ? null : userId.longValue())
                .createdAt(createdAt == null ? 0L : createdAt)
                .type(LogType.fromCode(type == null ? 0 : type))
                .content(content)
                .username(username)
                .tokenName(tokenName)
                .modelName(modelName)
                .quota(quota == null ? 0L : quota)
                .promptTokens(promptTokens == null ? 0 : promptTokens)
                .completionTokens(completionTokens == null ? 0 : completionTokens)
                .useTime(useTime == null ? 0 : useTime)
                .stream(Boolean.TRUE.equals(isStream))
                .channelId(channelId == null ? null : channelId.longValue())
                .channelName(channelName)
                .tokenId(tokenId == null ? null : tokenId.longValue())
                .group(group)
                .ip(ip)
                .requestId(requestId)
                .upstreamRequestId(upstreamRequestId)
                .other(other)
                .requestedModel(requestedModel)
                .resolvedPublicModel(resolvedPublicModel)
                .actualUpstreamModel(actualUpstreamModel)
                .inboundProtocol(inboundProtocol)
                .upstreamProtocol(upstreamProtocol)
                .protocolConverted(Boolean.TRUE.equals(protocolConverted))
                .userAgent(userAgent)
                .quotaSell(quotaSell == null ? 0 : quotaSell)
                .quotaCost(quotaCost == null ? 0 : quotaCost)
                .quotaProfit(quotaProfit == null ? 0 : quotaProfit)
                .build();
    }

    /**
     * 领域日志 → PO（审计写方向，F-4011/F-4013）：只映射 who/what/when/where 五字段
     * （user_id/created_at/type/content/username/ip），其余消费维度（token/model/quota...）留默认
     * （null/0）。{@code userId} 窄化为 Integer（logs 表 int 列，值域受控）。{@code type} null→0。
     * 逻辑与原 {@code LogRepositoryImpl#recordAudit} 的建 PO 段 1:1。
     *
     * @param entry 审计日志领域对象（非空）
     * @return 待持久化的 PO（仅审计字段）
     */
    public static LogReadPO ofAudit(LogEntry entry) {
        LogReadPO e = new LogReadPO();
        e.userId = entry.userId() == null ? null : entry.userId().intValue();
        e.createdAt = entry.createdAt();
        e.type = entry.type() == null ? 0 : entry.type().code();
        e.content = entry.content();
        e.username = entry.username();
        e.ip = entry.ip();
        return e;
    }
}
