package com.nexa.model.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/**
 * 模型元数据 JPA 持久化实体（基础设施层，对齐 V10 {@code model_metas} 与 DB-SCHEMA 模块四 §4.1）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.model.domain.model.ModelMeta} 分离（DDD：domain 不感知 JPA）。
 * 映射转换在 {@code ModelMetaRepositoryImpl}。软删除用 {@code deleted_at} + {@code @SQLRestriction}，
 * 查询自动过滤已删行。description/icon/tags/endpoints/name_rule 用 text 承载（可长）。</p>
 */
@Entity
@Table(name = "model_metas", indexes = {
        @Index(name = "idx_model_metas_vendor_id", columnList = "vendor_id"),
        @Index(name = "idx_model_metas_deleted_at", columnList = "deleted_at")
})
@SQLRestriction("deleted_at IS NULL")
public class ModelMetaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_name", nullable = false, length = 255)
    private String modelName;

    @Column(name = "status", nullable = false)
    private int status;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "icon", columnDefinition = "text")
    private String icon;

    @Column(name = "tags", columnDefinition = "text")
    private String tags;

    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "endpoints", columnDefinition = "text")
    private String endpoints;

    @Column(name = "name_rule", columnDefinition = "text")
    private String nameRule;

    @Column(name = "sync_official", nullable = false)
    private int syncOfficial;

    @Column(name = "created_time")
    private Long createdTime;

    @Column(name = "updated_time")
    private Long updatedTime;

    @Column(name = "deleted_at")
    private Long deletedAt;

    /** JPA 规范要求的无参构造器。 */
    public ModelMetaJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
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

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }

    public String getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(String endpoints) {
        this.endpoints = endpoints;
    }

    public String getNameRule() {
        return nameRule;
    }

    public void setNameRule(String nameRule) {
        this.nameRule = nameRule;
    }

    public int getSyncOfficial() {
        return syncOfficial;
    }

    public void setSyncOfficial(int syncOfficial) {
        this.syncOfficial = syncOfficial;
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
