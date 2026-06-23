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

/**
 * 超管底仓映射 JPA 持久化实体（基础设施层，对齐 V12 {@code platform_model_mappings}，DB-SCHEMA §17）。
 */
@Entity(name = "ModelPlatformModelMappingJpaEntity")
@Table(name = "platform_model_mappings", indexes = {
        @Index(name = "idx_platform_model_mappings_deleted_at", columnList = "deleted_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_pmm_public_name", columnNames = {"public_name"})
})
@SQLRestriction("deleted_at IS NULL")
public class PlatformModelMappingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_name", nullable = false, length = 255)
    private String publicName;

    @Column(name = "upstream_name", nullable = false, length = 255)
    private String upstreamName;

    @Column(name = "enabled", columnDefinition = "boolean default true")
    private Boolean enabled;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_time")
    private Long createdTime;

    @Column(name = "updated_time")
    private Long updatedTime;

    @Column(name = "deleted_at")
    private Long deletedAt;

    /** JPA 规范要求的无参构造器。 */
    public PlatformModelMappingJpaEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicName() { return publicName; }
    public void setPublicName(String publicName) { this.publicName = publicName; }
    public String getUpstreamName() { return upstreamName; }
    public void setUpstreamName(String upstreamName) { this.upstreamName = upstreamName; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getCreatedTime() { return createdTime; }
    public void setCreatedTime(Long createdTime) { this.createdTime = createdTime; }
    public Long getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(Long updatedTime) { this.updatedTime = updatedTime; }
    public Long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
}
