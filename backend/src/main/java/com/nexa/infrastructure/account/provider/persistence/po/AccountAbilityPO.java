package com.nexa.infrastructure.account.provider.persistence.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Ability 路由索引 JPA 实体（账号级路由，V33 重建）。
 *
 * <p>承载 account_id × group × models 反向索引，用于快速账号选择。
 * 由 {@code AccountRepositoryImpl} 在账号保存时 fan-out 维护。</p>
 */
@Entity(name = "AccountAbilityPO")
@Table(name = "abilities", indexes = {
        @Index(name = "idx_abilities_group_models", columnList = "\"group\", models"),
        @Index(name = "idx_abilities_account_status", columnList = "account_id, status"),
        @Index(name = "idx_abilities_tag", columnList = "tag")
})
public class AccountAbilityPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "\"group\"", length = 255, nullable = false)
    private String group;

    @Column(name = "models", columnDefinition = "TEXT")
    private String models;

    @Column(name = "tag", length = 255)
    private String tag;

    @Column(name = "status", length = 50, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;

    public AccountAbilityPO() {
    }

    public AccountAbilityPO(Long accountId, String group, String models, String tag,
                           String status, Long createdAt, Long updatedAt) {
        this.accountId = accountId;
        this.group = group;
        this.models = models;
        this.tag = tag;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getModels() {
        return models;
    }

    public void setModels(String models) {
        this.models = models;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
