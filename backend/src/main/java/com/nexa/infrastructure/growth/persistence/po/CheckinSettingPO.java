package com.nexa.infrastructure.growth.persistence.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 签到配置 JPA 持久化实体（基础设施层，对齐 V18 {@code checkin_settings}，PRD GR-3 / FC-022）。
 *
 * <p>DB-SCHEMA §12 注：原系统 CheckinSetting 走 Option KV。本实现落库为<b>单行配置表</b>
 * （固定主键 {@link #SINGLETON_ID}=1），语义等价于一组 KV，但避免引入通用 Option 表的额外复杂度，
 * 且天然满足「全局唯一一份签到配置」。与领域值对象 {@link com.nexa.domain.growth.vo.CheckinSetting}
 * 分离，映射在 {@code CheckinSettingRepositoryImpl}。</p>
 */
@Entity
@Table(name = "checkin_settings")
public class CheckinSettingPO {

    /** 全局唯一配置行的固定主键（单例行）。 */
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "min_quota", nullable = false)
    private Long minQuota;

    @Column(name = "max_quota", nullable = false)
    private Long maxQuota;

    /** JPA 规范要求的无参构造器。 */
    public CheckinSettingPO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Long getMinQuota() {
        return minQuota;
    }

    public void setMinQuota(Long minQuota) {
        this.minQuota = minQuota;
    }

    public Long getMaxQuota() {
        return maxQuota;
    }

    public void setMaxQuota(Long maxQuota) {
        this.maxQuota = maxQuota;
    }
}
