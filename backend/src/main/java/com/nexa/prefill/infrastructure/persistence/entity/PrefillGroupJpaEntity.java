package com.nexa.prefill.infrastructure.persistence.entity;

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

/**
 * 预填分组 JPA 持久化实体（基础设施层，对齐 V10 {@code prefill_groups} 与 DB-SCHEMA §17）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.prefill.domain.model.PrefillGroup} 分离（DDD：domain
 * 不感知 JPA）。映射转换在 {@code PrefillGroupRepositoryImpl}。{@code items} 用 Hibernate 6
 * {@code @JdbcTypeCode(SqlTypes.JSON)} 以 String 承载 JSONB（JSON 字符串数组，DB-SCHEMA §17）；
 * 软删除用 {@code deleted_at} 时间戳 + {@code @SQLRestriction("deleted_at IS NULL")}（对齐
 * DB-SCHEMA 全文惯例），查询自动过滤已删行。{@code name} 唯一索引 {@code uk_prefill_name}。</p>
 */
@Entity
@Table(name = "prefill_groups", indexes = {
        @Index(name = "uk_prefill_name", columnList = "name", unique = true),
        @Index(name = "idx_prefill_groups_type", columnList = "type"),
        @Index(name = "idx_prefill_groups_deleted_at", columnList = "deleted_at")
})
@SQLRestriction("deleted_at IS NULL")
public class PrefillGroupJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 64, nullable = false, unique = true)
    private String name;

    @Column(name = "type", length = 32, nullable = false)
    private String type;

    /** 条目 JSON 字符串数组（如 {@code ["gpt-4o","gpt-3.5-turbo"]}），JSONB 承载。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items", columnDefinition = "jsonb")
    private String items;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_time")
    private Long createdTime;

    @Column(name = "updated_time")
    private Long updatedTime;

    @Column(name = "deleted_at")
    private Long deletedAt;

    /** JPA 规范要求的无参构造器。 */
    public PrefillGroupJpaEntity() {
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getItems() {
        return items;
    }

    public void setItems(String items) {
        this.items = items;
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
