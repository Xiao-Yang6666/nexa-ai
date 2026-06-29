package com.nexa.infrastructure.token.persistence.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * 令牌 JPA 持久化实体（基础设施层，对齐 V9 {@code tokens} 与 DB-SCHEMA §2）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.token.model.Token} 分离（DDD：domain 不感知 JPA）。
 * 映射转换在 {@code TokenRepositoryImpl}。{@code key} 落库但绝不进默认客户视图 DTO（仅受控取明文端点用）。</p>
 *
 * <p>{@code model_limits}/{@code endpoint_limits} 用 Hibernate 6 {@code @JdbcTypeCode(SqlTypes.JSON)}
 * 以 String 承载 JSONB。{@code key}/{@code group} 为 PG 保留字，列名加双引号转义（与 V9 一致）。
 * 软删除用 {@code deleted_at} 时间戳 + {@code @SQLRestriction("deleted_at IS NULL")}（对齐 DB-SCHEMA
 * 全文惯例），查询自动过滤已删行。</p>
 */
@Entity
@Table(name = "tokens", indexes = {
        @Index(name = "idx_tokens_user_id", columnList = "user_id"),
        @Index(name = "idx_tokens_name", columnList = "name"),
        @Index(name = "idx_tokens_deleted_at", columnList = "deleted_at")
})
@SQLRestriction("deleted_at IS NULL")
public class TokenPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "\"key\"", columnDefinition = "varchar(128)", unique = true)
    private String key;

    @Column(name = "status", nullable = false)
    private int status;

    @Column(name = "name")
    private String name;

    @Column(name = "created_time")
    private Long createdTime;

    @Column(name = "accessed_time")
    private Long accessedTime;

    @Column(name = "expired_time", nullable = false)
    private long expiredTime;

    @Column(name = "remain_quota", nullable = false)
    private long remainQuota;

    @Column(name = "unlimited_quota", nullable = false)
    private boolean unlimitedQuota;

    @Column(name = "model_limits_enabled", nullable = false)
    private boolean modelLimitsEnabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_limits", columnDefinition = "jsonb")
    private String modelLimits;

    @Column(name = "allow_ips", nullable = false, columnDefinition = "text")
    private String allowIps;

    @Column(name = "used_quota", nullable = false)
    private long usedQuota;

    @Column(name = "\"group\"", nullable = false, length = 255)
    private String group;

    @Column(name = "cross_group_retry", nullable = false)
    private boolean crossGroupRetry;

    @Column(name = "endpoint_limits_enabled", nullable = false)
    private boolean endpointLimitsEnabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "endpoint_limits", columnDefinition = "jsonb")
    private String endpointLimits;

    @Column(name = "deleted_at")
    private Long deletedAt;

    /** JPA 规范要求的无参构造器。 */
    public TokenPO() {
    }

    // ---- 访问器（JPA 需要 getter/setter；领域逻辑不在此） ----

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
}
