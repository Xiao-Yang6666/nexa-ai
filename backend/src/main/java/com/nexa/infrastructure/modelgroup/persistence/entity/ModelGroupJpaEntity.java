package com.nexa.infrastructure.modelgroup.persistence.entity;

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

import java.math.BigDecimal;

/**
 * 模型组 JPA 持久化实体（基础设施层，对齐 V26 {@code model_groups}）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.modelgroup.model.ModelGroup} 分离（DDD：domain
 * 不感知 JPA）。映射转换在 {@code ModelGroupRepositoryImpl}。{@code models} 用 Hibernate 6
 * {@code @JdbcTypeCode(SqlTypes.JSON)} 以 String 承载 JSONB（模型名 JSON 字符串数组）；软删除用
 * {@code deleted_at} + {@code @SQLRestriction}。{@code code} 唯一索引 {@code uk_model_group_code}。</p>
 */
@Entity
@Table(name = "model_groups", indexes = {
        @Index(name = "uk_model_group_code", columnList = "code", unique = true),
        @Index(name = "idx_model_groups_status", columnList = "status"),
        @Index(name = "idx_model_groups_access_policy", columnList = "access_policy"),
        @Index(name = "idx_model_groups_deleted_at", columnList = "deleted_at")
})
@SQLRestriction("deleted_at IS NULL")
public class ModelGroupJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 64, nullable = false)
    private String name;

    @Column(name = "code", length = 64, nullable = false, unique = true)
    private String code;

    @Column(name = "base_price_ratio", nullable = false, precision = 18, scale = 6)
    private BigDecimal basePriceRatio;

    /** 可用模型 JSON 字符串数组（如 {@code ["gpt-4o","claude-3-opus"]}），JSONB 承载。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "models", columnDefinition = "jsonb")
    private String models;

    @Column(name = "access_policy", length = 20, nullable = false)
    private String accessPolicy;

    @Column(name = "status", nullable = false)
    private int status;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_time")
    private Long createdTime;

    @Column(name = "updated_time")
    private Long updatedTime;

    @Column(name = "deleted_at")
    private Long deletedAt;

    /** JPA 规范要求的无参构造器。 */
    public ModelGroupJpaEntity() {
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public BigDecimal getBasePriceRatio() {
        return basePriceRatio;
    }

    public void setBasePriceRatio(BigDecimal basePriceRatio) {
        this.basePriceRatio = basePriceRatio;
    }

    public String getModels() {
        return models;
    }

    public void setModels(String models) {
        this.models = models;
    }

    public String getAccessPolicy() {
        return accessPolicy;
    }

    public void setAccessPolicy(String accessPolicy) {
        this.accessPolicy = accessPolicy;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public Long getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Long deletedAt) {
        this.deletedAt = deletedAt;
    }
}
