package com.nexa.infrastructure.relay.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 客户层别名 JPA 持久化实体（基础设施层，对齐 V13 {@code user_model_aliases} 与 DB-SCHEMA §21）。
 *
 * <p>与领域聚合 {@link com.nexa.domain.relay.model.UserModelAlias} 分离。复合唯一键
 * (scope_type, scope_id, alias) 由 V13 唯一索引 {@code uk_scope_alias} 守护（部分索引含 deleted_at IS NULL）。</p>
 */
@Entity(name = "RelayUserModelAliasJpaEntity")
@Table(name = "user_model_aliases", indexes = {
        @Index(name = "idx_user_model_aliases_deleted_at", columnList = "deleted_at")
})
public class UserModelAliasJpaEntity {

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

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_time", nullable = false)
    private Long createdTime;

    @Column(name = "updated_time", nullable = false)
    private Long updatedTime;

    @Column(name = "deleted_at")
    private Long deletedAt;

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
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Long getCreatedTime() { return createdTime; }
    public void setCreatedTime(Long createdTime) { this.createdTime = createdTime; }
    public Long getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(Long updatedTime) { this.updatedTime = updatedTime; }
    public Long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
}
