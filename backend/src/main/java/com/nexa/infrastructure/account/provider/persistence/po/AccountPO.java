package com.nexa.infrastructure.account.provider.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.account.provider.model.Account;
import com.nexa.domain.account.provider.vo.AccountGroupRef;
import com.nexa.infrastructure.persistence.JsonbStringTypeHandler;

import java.util.List;

/**
 * 供应商账号持久化实体（基础设施层，对齐 V29 {@code accounts}）。
 *
 * <p>持久化映射，与领域聚合 {@link Account} 分离（DDD：domain 不感知持久化框架）。映射由本类就近工厂
 * {@link #toDomain(List)} / {@link #of(Account)} 承载。{@code credentials} / {@code model_mapping} 是
 * PG {@code jsonb} 列，以 String 承载——MyBatis-Plus 侧由 {@link JsonbStringTypeHandler} 完成 String↔jsonb
 * 互转（{@code autoResultMap = true} 使读取亦走该 Handler），并存期保留 JPA 的 {@code @JdbcTypeCode(SqlTypes.JSON)}。
 * 时间字段统一 epoch 秒 Long；status 以字符串码持久化。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：保留全部 JPA 注解满足 {@code ddl-auto=validate}，新增 MyBatis-Plus 注解供 Mapper 读写。</p>
 */
@TableName(value = "accounts", autoResultMap = true)
public class AccountPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("platform")
    private String platform;

    @TableField("type")
    private String type;

    @TableField(value = "credentials", typeHandler = JsonbStringTypeHandler.class)
    private String credentials;

    @TableField("base_url")
    private String baseUrl;

    @TableField("concurrency")
    private int concurrency;

    @TableField("priority")
    private int priority;

    @TableField("status")
    private String status;

    @TableField("rate_limited_at")
    private Long rateLimitedAt;

    @TableField("rate_limit_reset_at")
    private Long rateLimitResetAt;

    @TableField("overload_until")
    private Long overloadUntil;

    @TableField("expires_at")
    private Long expiresAt;

    @TableField("auto_pause_on_expired")
    private boolean autoPauseOnExpired;

    @TableField("rate_multiplier")
    private java.math.BigDecimal rateMultiplier;

    @TableField(value = "model_mapping", typeHandler = JsonbStringTypeHandler.class)
    private String modelMapping;

    @TableField("weight")
    private int weight;

    @TableField("tag")
    private String tag;

    @TableField("auto_ban")
    private boolean autoBan;

    @TableField("response_time")
    private Integer responseTime;

    @TableField("test_time")
    private Long testTime;

    @TableField("balance")
    private java.math.BigDecimal balance;

    @TableField("used_quota")
    private java.math.BigDecimal usedQuota;

    @TableField("models")
    private String models;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;

    /** 框架（JPA / MyBatis-Plus）实例化所需的无参构造器。 */
    public AccountPO() {
    }

    // ---- 访问器（JPA 需要 getter/setter；领域逻辑不在此） ----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCredentials() {
        return credentials;
    }

    public void setCredentials(String credentials) {
        this.credentials = credentials;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getRateLimitedAt() {
        return rateLimitedAt;
    }

    public void setRateLimitedAt(Long rateLimitedAt) {
        this.rateLimitedAt = rateLimitedAt;
    }

    public Long getRateLimitResetAt() {
        return rateLimitResetAt;
    }

    public void setRateLimitResetAt(Long rateLimitResetAt) {
        this.rateLimitResetAt = rateLimitResetAt;
    }

    public Long getOverloadUntil() {
        return overloadUntil;
    }

    public void setOverloadUntil(Long overloadUntil) {
        this.overloadUntil = overloadUntil;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isAutoPauseOnExpired() {
        return autoPauseOnExpired;
    }

    public void setAutoPauseOnExpired(boolean autoPauseOnExpired) {
        this.autoPauseOnExpired = autoPauseOnExpired;
    }

    public java.math.BigDecimal getRateMultiplier() {
        return rateMultiplier;
    }

    public void setRateMultiplier(java.math.BigDecimal rateMultiplier) {
        this.rateMultiplier = rateMultiplier;
    }

    public String getModelMapping() {
        return modelMapping;
    }

    public void setModelMapping(String modelMapping) {
        this.modelMapping = modelMapping;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public boolean isAutoBan() {
        return autoBan;
    }

    public void setAutoBan(boolean autoBan) {
        this.autoBan = autoBan;
    }

    public Integer getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(Integer responseTime) {
        this.responseTime = responseTime;
    }

    public Long getTestTime() {
        return testTime;
    }

    public void setTestTime(Long testTime) {
        this.testTime = testTime;
    }

    public java.math.BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(java.math.BigDecimal balance) {
        this.balance = balance;
    }

    public java.math.BigDecimal getUsedQuota() {
        return usedQuota;
    }

    public void setUsedQuota(java.math.BigDecimal usedQuota) {
        this.usedQuota = usedQuota;
    }

    public String getModels() {
        return models;
    }

    public void setModels(String models) {
        this.models = models;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * PO → 领域聚合（重建方向）。{@code credentials} 为 {@code "{}"} 占位时归一为 null（领域语义：无凭据）。
     * 分组关联由调用方从 {@code account_groups} 单独加载后传入（PO 不持有关联子表）。
     *
     * @param groups 该账号的分组关联（调用方加载）
     * @return 重建的账号聚合
     */
    public Account toDomain(List<AccountGroupRef> groups) {
        return Account.rehydrate(
                id,
                name,
                platform,
                type,
                "{}".equals(credentials) ? null : credentials,
                baseUrl,
                concurrency,
                priority,
                status,
                rateLimitedAt,
                rateLimitResetAt,
                overloadUntil,
                expiresAt,
                autoPauseOnExpired,
                rateMultiplier,
                modelMapping,
                weight,
                tag,
                autoBan,
                responseTime,
                testTime,
                balance,
                usedQuota,
                models,
                groups,
                createdAt,
                updatedAt);
    }

    /**
     * 领域聚合 → PO（持久化方向）。{@code credentials} 为 null 时落 {@code "{}"} 空 jsonb 占位。
     * 分组关联不在此映射（由调用方 fan-out 到 {@code account_groups}）。
     *
     * @param a 账号聚合（非空）
     * @return 待持久化的 PO
     */
    public static AccountPO of(Account a) {
        AccountPO e = new AccountPO();
        e.id = a.id();
        e.name = a.name();
        e.platform = a.platform();
        e.type = a.type();
        e.credentials = a.credentials() == null ? "{}" : a.credentials();
        e.baseUrl = a.baseUrl();
        e.concurrency = a.concurrency();
        e.priority = a.priority();
        e.status = a.status().code();
        e.rateLimitedAt = a.rateLimitedAt();
        e.rateLimitResetAt = a.rateLimitResetAt();
        e.overloadUntil = a.overloadUntil();
        e.expiresAt = a.expiresAt();
        e.autoPauseOnExpired = a.autoPauseOnExpired();
        e.rateMultiplier = a.rateMultiplier();
        e.modelMapping = a.modelMapping();
        e.weight = a.weight();
        e.tag = a.tag();
        e.autoBan = a.autoBan();
        e.responseTime = a.responseTime();
        e.testTime = a.testTime();
        e.balance = a.balance();
        e.usedQuota = a.usedQuota();
        e.models = a.models();
        e.createdAt = a.createdTime();
        e.updatedAt = a.updatedTime();
        return e;
    }
}
