package com.nexa.infrastructure.growth.persistence.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 签到记录 JPA 持久化实体（基础设施层，对齐 V17 {@code checkins} 与 DB-SCHEMA §12）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.growth.model.Checkin} 分离（DDD：domain 不感知 JPA）。
 * 映射转换在 {@code CheckinRepositoryImpl}。复合唯一约束 {@code idx_user_checkin_date (user_id,
 * checkin_date)} 守护「同一用户同日仅一条」不变量（PRD GR-1 §4 并发重复拦截）。</p>
 */
@Entity
@Table(name = "checkins", uniqueConstraints = {
        @UniqueConstraint(name = "idx_user_checkin_date", columnNames = {"user_id", "checkin_date"})
})
public class CheckinPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "checkin_date", columnDefinition = "varchar(10)", nullable = false)
    private String checkinDate;

    @Column(name = "quota_awarded", nullable = false)
    private Long quotaAwarded;

    @Column(name = "created_at")
    private Long createdAt;

    /** JPA 规范要求的无参构造器。 */
    public CheckinPO() {
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

    public String getCheckinDate() {
        return checkinDate;
    }

    public void setCheckinDate(String checkinDate) {
        this.checkinDate = checkinDate;
    }

    public Long getQuotaAwarded() {
        return quotaAwarded;
    }

    public void setQuotaAwarded(Long quotaAwarded) {
        this.quotaAwarded = quotaAwarded;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
