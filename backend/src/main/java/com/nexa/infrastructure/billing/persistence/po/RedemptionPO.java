package com.nexa.infrastructure.billing.persistence.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/**
 * 兑换码 JPA 持久化实体（基础设施层）。
 *
 * <p>对齐 DB-SCHEMA §6 Redemption / 表 {@code redemptions}。与领域聚合
 * {@link com.nexa.domain.billing.model.Redemption} 分离（DDD：domain 不感知 JPA），映射转换在
 * {@code RedemptionRepositoryImpl}。{@code key} 为 PG 保留字，列名双引号转义；软删除沿用
 * {@code deleted_at} + {@code @SQLRestriction}。</p>
 */
@Entity
@Table(name = "redemptions", indexes = {
        @Index(name = "idx_redemptions_key", columnList = "\"key\"", unique = true),
        @Index(name = "idx_redemptions_name", columnList = "name"),
        @Index(name = "idx_redemptions_deleted_at", columnList = "deleted_at")
})
@SQLRestriction("deleted_at IS NULL")
public class RedemptionPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;

    /** 兑换码明文（char(32)，PG 保留字 key 需双引号转义；唯一索引）。 */
    @Column(name = "\"key\"", columnDefinition = "varchar(32)", unique = true)
    private String key;

    @Column(name = "status", columnDefinition = "integer default 1")
    private Integer status;

    @Column(name = "name")
    private String name;

    @Column(name = "quota", columnDefinition = "integer default 100")
    private Integer quota;

    @Column(name = "created_time", columnDefinition = "bigint")
    private Long createdTime;

    @Column(name = "redeemed_time", columnDefinition = "bigint")
    private Long redeemedTime;

    @Column(name = "used_user_id")
    private Integer usedUserId;

    @Column(name = "expired_time", columnDefinition = "bigint")
    private Long expiredTime;

    @Column(name = "deleted_at")
    private Long deletedAt;

    /** JPA 规范要求的无参构造器。 */
    public RedemptionPO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getQuota() {
        return quota;
    }

    public void setQuota(Integer quota) {
        this.quota = quota;
    }

    public Long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Long createdTime) {
        this.createdTime = createdTime;
    }

    public Long getRedeemedTime() {
        return redeemedTime;
    }

    public void setRedeemedTime(Long redeemedTime) {
        this.redeemedTime = redeemedTime;
    }

    public Integer getUsedUserId() {
        return usedUserId;
    }

    public void setUsedUserId(Integer usedUserId) {
        this.usedUserId = usedUserId;
    }

    public Long getExpiredTime() {
        return expiredTime;
    }

    public void setExpiredTime(Long expiredTime) {
        this.expiredTime = expiredTime;
    }

    public Long getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Long deletedAt) {
        this.deletedAt = deletedAt;
    }
}
