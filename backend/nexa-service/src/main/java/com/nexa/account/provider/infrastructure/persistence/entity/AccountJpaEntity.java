package com.nexa.account.provider.infrastructure.persistence.entity;

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
 * 供应商账号 JPA 持久化实体（基础设施层，对齐 V29 {@code accounts}）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.account.provider.domain.model.Account} 分离
 * （DDD：domain 不感知 JPA）。映射转换在 {@code AccountRepositoryImpl}。
 * {@code credentials} 用 {@code @JdbcTypeCode(SqlTypes.JSON)} 以 String 承载 jsonb，落库但绝不进视图 DTO。
 * 时间字段统一 epoch 秒 Long；status 以字符串码持久化。</p>
 */
@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "idx_accounts_platform", columnList = "platform"),
        @Index(name = "idx_accounts_status", columnList = "status"),
        @Index(name = "idx_accounts_priority", columnList = "priority")
})
public class AccountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "platform", nullable = false, length = 50)
    private String platform;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "credentials", nullable = false, columnDefinition = "jsonb")
    private String credentials;

    @Column(name = "concurrency", nullable = false)
    private int concurrency;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "rate_limited_at")
    private Long rateLimitedAt;

    @Column(name = "rate_limit_reset_at")
    private Long rateLimitResetAt;

    @Column(name = "overload_until")
    private Long overloadUntil;

    @Column(name = "expires_at")
    private Long expiresAt;

    @Column(name = "auto_pause_on_expired", nullable = false)
    private boolean autoPauseOnExpired;

    @Column(name = "rate_multiplier", nullable = false, precision = 10, scale = 4)
    private java.math.BigDecimal rateMultiplier;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;

    /** JPA 规范要求的无参构造器。 */
    public AccountJpaEntity() {
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
}
