package com.nexa.model.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * 对外模型商品目录 JPA 持久化实体（基础设施层，对齐 V11 {@code public_models} 与 DB-SCHEMA §16）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.model.domain.model.PublicModel} 分离。
 * 软删除用 {@code deleted_at} + {@code @SQLRestriction("deleted_at IS NULL")}。</p>
 */
@Entity
@Table(name = "public_models", indexes = {
        @Index(name = "idx_public_models_quality_tier", columnList = "quality_tier"),
        @Index(name = "idx_public_models_deleted_at", columnList = "deleted_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_public_models_public_name", columnNames = {"public_name"})
})
@SQLRestriction("deleted_at IS NULL")
public class PublicModelJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_name", nullable = false, length = 255)
    private String publicName;

    @Column(name = "quality_tier", length = 32)
    private String qualityTier;

    @Column(name = "base_price_ratio", columnDefinition = "numeric default 0")
    private BigDecimal basePriceRatio;

    @Column(name = "use_price", columnDefinition = "boolean default false")
    private Boolean usePrice;

    @Column(name = "base_price", columnDefinition = "numeric default 0")
    private BigDecimal basePrice;

    @Column(name = "enabled", columnDefinition = "boolean default true")
    private Boolean enabled;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "created_time")
    private Long createdTime;

    @Column(name = "updated_time")
    private Long updatedTime;

    @Column(name = "deleted_at")
    private Long deletedAt;

    /** JPA 规范要求的无参构造器。 */
    public PublicModelJpaEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicName() { return publicName; }
    public void setPublicName(String publicName) { this.publicName = publicName; }
    public String getQualityTier() { return qualityTier; }
    public void setQualityTier(String qualityTier) { this.qualityTier = qualityTier; }
    public BigDecimal getBasePriceRatio() { return basePriceRatio; }
    public void setBasePriceRatio(BigDecimal basePriceRatio) { this.basePriceRatio = basePriceRatio; }
    public Boolean getUsePrice() { return usePrice; }
    public void setUsePrice(Boolean usePrice) { this.usePrice = usePrice; }
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCreatedTime() { return createdTime; }
    public void setCreatedTime(Long createdTime) { this.createdTime = createdTime; }
    public Long getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(Long updatedTime) { this.updatedTime = updatedTime; }
    public Long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
}
