package com.nexa.infrastructure.model.persistence.po;

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
 * 客户层自助映射 JPA 持久化实体（基础设施层，对齐 V13 {@code user_model_aliases}，DB-SCHEMA §18）。
 */
@Entity(name = "ModelUserModelAliasJpaEntity")
@Table(name = "user_model_aliases", indexes = {
        @Index(name = "idx_user_model_aliases_deleted_at", columnList = "deleted_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_scope_alias", columnNames = {"scope_type", "scope_id", "alias"})
})
@SQLRestriction("deleted_at IS NULL")
public class UserModelAliasPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope_type", nullable = false, length = 16)
    private String scopeType;

    @Column(name = "scope_id", nullable = false, length = 64)
    private String scopeId;

    @Column(name = "alias", nullable = false, length = 255)
    private String alias;

    @Column(name = "target", nullable = false, length = 255)
    private String target;

    @Column(name = "enabled", columnDefinition = "boolean default true")
    private Boolean enabled;

    @Column(name = "created_time")
    private Long createdTime;

    @Column(name = "updated_time")
    private Long updatedTime;

    @Column(name = "deleted_at")
    private Long deletedAt;

    /** JPA 规范要求的无参构造器。 */
    public UserModelAliasPO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public String getScopeId() { return scopeId; }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Long getCreatedTime() { return createdTime; }
    public void setCreatedTime(Long createdTime) { this.createdTime = createdTime; }
    public Long getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(Long updatedTime) { this.updatedTime = updatedTime; }
    public Long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
}
