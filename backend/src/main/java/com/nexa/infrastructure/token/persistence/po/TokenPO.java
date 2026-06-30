package com.nexa.infrastructure.token.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.token.model.Token;
import com.nexa.domain.token.vo.TokenStatus;
import com.nexa.infrastructure.persistence.JsonbStringTypeHandler;

/**
 * 令牌持久化实体（基础设施层，对齐 V9 {@code tokens} 与 DB-SCHEMA §2）。
 *
 * <p>持久化映射，与领域聚合 {@link Token} 分离（DDD：domain 不感知持久化框架）。映射由本类就近工厂
 * {@link #toDomain()} / {@link #of(Token)} 承载。{@code key} 落库但绝不进默认客户视图 DTO（仅受控取明文端点用）。</p>
 *
 * <p>{@code model_limits}/{@code endpoint_limits} 是 PG {@code jsonb} 列，以 String 承载——MyBatis-Plus 侧由
 * {@link JsonbStringTypeHandler} 完成 String↔jsonb 互转（{@code @TableName(autoResultMap = true)} 使读取亦走该 Handler），
 * 并存期保留 JPA 的 {@code @JdbcTypeCode(SqlTypes.JSON)}。{@code key}/{@code group} 为 PG 保留字，列名双引号转义。
 * 软删除用 {@code deleted_at} 时间戳：MyBatis-Plus 以 {@code @TableLogic(value = "null")} 自动过滤未删行
 * （等价 {@code @SQLRestriction}），软删除写由 Mapper 显式 {@code @Update} 打 epoch 秒（不用 deleteById）。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：保留全部 JPA 注解满足 {@code ddl-auto=validate}，新增 MyBatis-Plus 注解供 Mapper 读写。</p>
 */
@TableName(value = "tokens", autoResultMap = true)
public class TokenPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Integer userId;

    @TableField("\"key\"")
    private String key;

    @TableField("status")
    private int status;

    @TableField("name")
    private String name;

    @TableField("created_time")
    private Long createdTime;

    @TableField("accessed_time")
    private Long accessedTime;

    @TableField("expired_time")
    private long expiredTime;

    @TableField("remain_quota")
    private long remainQuota;

    @TableField("unlimited_quota")
    private boolean unlimitedQuota;

    @TableField("model_limits_enabled")
    private boolean modelLimitsEnabled;

    @TableField(value = "model_limits", typeHandler = JsonbStringTypeHandler.class)
    private String modelLimits;

    @TableField("allow_ips")
    private String allowIps;

    @TableField("used_quota")
    private long usedQuota;

    @TableField("\"group\"")
    private String group;

    @TableField("cross_group_retry")
    private boolean crossGroupRetry;

    @TableField("endpoint_limits_enabled")
    private boolean endpointLimitsEnabled;

    @TableField(value = "endpoint_limits", typeHandler = JsonbStringTypeHandler.class)
    private String endpointLimits;

    @TableField("deleted_at")
    @TableLogic(value = "null")
    private Long deletedAt;

    /** 框架（MyBatis-Plus）实例化所需的无参构造器。 */
    public TokenPO() {
    }

    // ---- 访问器（getter/setter；领域逻辑不在此） ----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Long createdTime) {
        this.createdTime = createdTime;
    }

    public Long getAccessedTime() {
        return accessedTime;
    }

    public void setAccessedTime(Long accessedTime) {
        this.accessedTime = accessedTime;
    }

    public long getExpiredTime() {
        return expiredTime;
    }

    public void setExpiredTime(long expiredTime) {
        this.expiredTime = expiredTime;
    }

    public long getRemainQuota() {
        return remainQuota;
    }

    public void setRemainQuota(long remainQuota) {
        this.remainQuota = remainQuota;
    }

    public boolean isUnlimitedQuota() {
        return unlimitedQuota;
    }

    public void setUnlimitedQuota(boolean unlimitedQuota) {
        this.unlimitedQuota = unlimitedQuota;
    }

    public boolean isModelLimitsEnabled() {
        return modelLimitsEnabled;
    }

    public void setModelLimitsEnabled(boolean modelLimitsEnabled) {
        this.modelLimitsEnabled = modelLimitsEnabled;
    }

    public String getModelLimits() {
        return modelLimits;
    }

    public void setModelLimits(String modelLimits) {
        this.modelLimits = modelLimits;
    }

    public String getAllowIps() {
        return allowIps;
    }

    public void setAllowIps(String allowIps) {
        this.allowIps = allowIps;
    }

    public long getUsedQuota() {
        return usedQuota;
    }

    public void setUsedQuota(long usedQuota) {
        this.usedQuota = usedQuota;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public boolean isCrossGroupRetry() {
        return crossGroupRetry;
    }

    public void setCrossGroupRetry(boolean crossGroupRetry) {
        this.crossGroupRetry = crossGroupRetry;
    }

    public boolean isEndpointLimitsEnabled() {
        return endpointLimitsEnabled;
    }

    public void setEndpointLimitsEnabled(boolean endpointLimitsEnabled) {
        this.endpointLimitsEnabled = endpointLimitsEnabled;
    }

    public String getEndpointLimits() {
        return endpointLimits;
    }

    public void setEndpointLimits(String endpointLimits) {
        this.endpointLimits = endpointLimits;
    }

    public Long getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Long deletedAt) {
        this.deletedAt = deletedAt;
    }

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * 领域聚合 → PO（持久化方向）。userId 由 long 安全窄化为 DB INTEGER；jsonb 列空串归 null
     * （避免 PG 解析空串 JSON 报错）；allowIps/group 的 null 归一空串。
     *
     * @param token 令牌聚合
     * @return 待持久化的 PO
     */
    public static TokenPO of(Token token) {
        TokenPO e = new TokenPO();
        e.id = token.id();
        e.userId = toIntUserId(token.userId());
        e.key = token.key();
        e.status = token.status().code();
        e.name = token.name();
        e.createdTime = token.createdTime();
        e.accessedTime = token.accessedTime();
        e.expiredTime = token.expiredTime();
        e.remainQuota = token.remainQuota();
        e.unlimitedQuota = token.unlimitedQuota();
        e.modelLimitsEnabled = token.modelLimitsEnabled();
        e.modelLimits = emptyToNull(token.modelLimits());
        e.allowIps = token.allowIps() == null ? "" : token.allowIps();
        e.usedQuota = token.usedQuota();
        e.group = token.group() == null ? "" : token.group();
        e.crossGroupRetry = token.crossGroupRetry();
        e.endpointLimitsEnabled = token.endpointLimitsEnabled();
        e.endpointLimits = emptyToNull(token.endpointLimits());
        return e;
    }

    /**
     * PO → 领域聚合（重建方向，走 {@link Token#builder()}）。数值列 null 兜底与 name/allowIps/group/
     * endpointLimits 的 null 归一空串统一收敛在 {@code Token.Builder} 内，这里只做状态码解析与
     * modelLimits 空串归一（其余直传）。
     *
     * @return 重建的令牌聚合
     */
    public Token toDomain() {
        return Token.builder()
                .id(id)
                .userId(userId == null ? null : userId.longValue())
                .key(key)
                .status(TokenStatus.fromCode(status))
                .name(name)
                .expiredTime(expiredTime)
                .remainQuota(remainQuota)
                .unlimitedQuota(unlimitedQuota)
                .modelLimitsEnabled(modelLimitsEnabled)
                .modelLimits(modelLimits == null ? "" : modelLimits)
                .allowIps(allowIps)
                .usedQuota(usedQuota)
                .group(group)
                .crossGroupRetry(crossGroupRetry)
                .endpointLimitsEnabled(endpointLimitsEnabled)
                .endpointLimits(endpointLimits)
                .accessedTime(accessedTime)
                .createdTime(createdTime)
                .build();
    }

    /**
     * 将领域 long 用户 id 安全转为 DB INTEGER（越界即抛，不静默截断）。DB-SCHEMA §2 user_id 列为 INTEGER，
     * 领域/接口层用 long 接收（AuthenticatedActor.userId 为 long）；BIGSERIAL 在 INTEGER 范围内长期不溢出。
     */
    private static int toIntUserId(long userId) {
        if (userId > Integer.MAX_VALUE || userId < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("userId out of INTEGER range: " + userId);
        }
        return (int) userId;
    }

    /** JSONB 列空串归 null（避免 PG 解析空串 JSON 报错；查询出来再归空串）。 */
    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
