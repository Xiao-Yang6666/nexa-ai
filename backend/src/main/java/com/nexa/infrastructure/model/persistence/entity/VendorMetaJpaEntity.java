package com.nexa.infrastructure.model.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/**
 * 供应商元数据 JPA 持久化实体（基础设施层，对齐 V10 {@code vendor_metas} 与 DB-SCHEMA 模块四 §4.2）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.model.model.Vendor} 分离（DDD：domain 不感知 JPA）。
 * 映射转换在 {@code VendorRepositoryImpl}。软删除用 {@code deleted_at} 时间戳 +
 * {@code @SQLRestriction("deleted_at IS NULL")}（与 tokens 等表惯例一致），查询自动过滤已删行。</p>
 */
@Entity
@Table(name = "vendor_metas", indexes = {
        @Index(name = "idx_vendor_metas_deleted_at", columnList = "deleted_at")
})
@SQLRestriction("deleted_at IS NULL")
public class VendorMetaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "icon", columnDefinition = "text")
    private String icon;

    @Column(name = "status", nullable = false)
    private int status;

    @Column(name = "created_time")
    private Long createdTime;

    @Column(name = "updated_time")
    private Long updatedTime;

    @Column(name = "deleted_at")
    private Long deletedAt;

    /** JPA 规范要求的无参构造器。 */
    public VendorMetaJpaEntity() {
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

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
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
