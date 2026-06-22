package com.nexa.relay.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 超管底仓映射 JPA 持久化实体（基础设施层，对齐 V12 {@code platform_model_mappings} 与 DB-SCHEMA §20）。
 *
 * <p>与领域聚合 {@link com.nexa.relay.domain.model.PlatformModelMapping} 分离（DDD：domain 不感知 JPA）。
 * {@code upstream_name}(B) 落库但绝不进客户视图 DTO（可见性铁律 ADR-COMPAT-05）。软删除用 deleted_at epoch。</p>
 */
@Entity(name = "RelayPlatformModelMappingJpaEntity")
@Table(name = "platform_model_mappings", indexes = {
        @Index(name = "idx_platform_model_mappings_deleted_at", columnList = "deleted_at")
})
public class PlatformModelMappingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_name", nullable = false, length = 255)
    private String publicName;

    @Column(name = "upstream_name", nullable = false, length = 255)
    private String upstreamName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_time", nullable = false)
    private Long createdTime;

    @Column(name = "updated_time", nullable = false)
    private Long updatedTime;

    @Column(name = "deleted_at")
    private Long deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicName() { return publicName; }
    public void setPublicName(String publicName) { this.publicName = publicName; }
    public String getUpstreamName() { return upstreamName; }
    public void setUpstreamName(String upstreamName) { this.upstreamName = upstreamName; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getCreatedTime() { return createdTime; }
    public void setCreatedTime(Long createdTime) { this.createdTime = createdTime; }
    public Long getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(Long updatedTime) { this.updatedTime = updatedTime; }
    public Long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
}
