package com.nexa.infrastructure.routing.persistence.entity;

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
 * 亲和规则 JPA 持久化实体（基础设施层，对齐 V9 {@code affinity_rules} 与 prd-channel CH-4）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.routing.model.AffinityRule} 分离（DDD：domain 不感知 JPA）。
 * 映射转换在 {@code AffinityRuleRepositoryImpl}。</p>
 *
 * <p>{@code key_sources}/{@code pass_headers} 用 Hibernate 6 {@code @JdbcTypeCode(SqlTypes.JSON)} 以 String
 * 承载 JSONB；仓储层与领域 VO/Map 互转（Jackson）。</p>
 */
@Entity
@Table(name = "affinity_rules", indexes = {
        @Index(name = "idx_affinity_rules_enabled", columnList = "enabled")
})
public class AffinityRuleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 64, unique = true)
    private String name;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "model_regex", nullable = false, columnDefinition = "text")
    private String modelRegex;

    @Column(name = "path_regex", nullable = false, columnDefinition = "text")
    private String pathRegex;

    @Column(name = "key_sources", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String keySources;

    @Column(name = "pass_headers", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String passHeaders;

    @Column(name = "skip_retry_on_failure", nullable = false)
    private boolean skipRetryOnFailure;

    @Column(name = "ttl_seconds", nullable = false)
    private long ttlSeconds;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @Column(name = "created_time")
    private Long createdTime;

    @Column(name = "updated_time")
    private Long updatedTime;

    /** JPA 缺省构造器（必备）。 */
    public AffinityRuleJpaEntity() {
    }

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModelRegex() {
        return modelRegex;
    }

    public void setModelRegex(String modelRegex) {
        this.modelRegex = modelRegex;
    }

    public String getPathRegex() {
        return pathRegex;
    }

    public void setPathRegex(String pathRegex) {
        this.pathRegex = pathRegex;
    }

    public String getKeySources() {
        return keySources;
    }

    public void setKeySources(String keySources) {
        this.keySources = keySources;
    }

    public String getPassHeaders() {
        return passHeaders;
    }

    public void setPassHeaders(String passHeaders) {
        this.passHeaders = passHeaders;
    }

    public boolean isSkipRetryOnFailure() {
        return skipRetryOnFailure;
    }

    public void setSkipRetryOnFailure(boolean skipRetryOnFailure) {
        this.skipRetryOnFailure = skipRetryOnFailure;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    public Long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Long createdTime) {
        this.createdTime = createdTime;
    }

    public Long getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(Long updatedTime) {
        this.updatedTime = updatedTime;
    }
}
