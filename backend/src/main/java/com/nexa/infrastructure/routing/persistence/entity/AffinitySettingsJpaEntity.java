package com.nexa.infrastructure.routing.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 亲和缓存全局策略 JPA 持久化实体（基础设施层，对齐 V9 {@code affinity_settings}，单例 id=1）。
 *
 * <p>与领域 {@link com.nexa.domain.routing.vo.AffinitySettings} 值对象分离。表上 CHECK(id=1) 强制单例，
 * 仓储实现层始终读写 id=1 行（{@code AffinityRuleRepositoryImpl#loadSettings/saveSettings}）。</p>
 */
@Entity
@Table(name = "affinity_settings")
public class AffinitySettingsJpaEntity {

    /** 单例 id（恒为 1）。 */
    @Id
    @Column(name = "id", nullable = false)
    private int id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "switch_on_success", nullable = false)
    private boolean switchOnSuccess;

    @Column(name = "max_entries", nullable = false)
    private int maxEntries;

    @Column(name = "default_ttl_seconds", nullable = false)
    private long defaultTtlSeconds;

    @Column(name = "updated_time")
    private Long updatedTime;

    public AffinitySettingsJpaEntity() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSwitchOnSuccess() {
        return switchOnSuccess;
    }

    public void setSwitchOnSuccess(boolean switchOnSuccess) {
        this.switchOnSuccess = switchOnSuccess;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public long getDefaultTtlSeconds() {
        return defaultTtlSeconds;
    }

    public void setDefaultTtlSeconds(long defaultTtlSeconds) {
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    public Long getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(Long updatedTime) {
        this.updatedTime = updatedTime;
    }
}
