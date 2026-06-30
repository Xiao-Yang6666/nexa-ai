package com.nexa.infrastructure.routing.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 亲和缓存全局策略持久化实体（基础设施层，对齐 V9 {@code affinity_settings}，单例 id=1）。
 *
 * <p>与领域 {@link com.nexa.domain.routing.vo.AffinitySettings} 值对象分离。表上 CHECK(id=1) 强制单例，
 * 仓储实现层始终读写 id=1 行（{@code AffinityRuleRepositoryImpl#loadSettings/saveSettings}）。VO↔PO 互转较简单
 * 但与规则仓储耦合在同一 Impl，故映射仍留在 Impl，本 PO 不内置就近工厂方法。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：本 PO 同时保留 JPA 注解（{@code @Entity/@Table/@Column/@Id}，
 * 满足并存期 {@code ddl-auto=validate} 全局启动校验）与 MyBatis-Plus 注解
 * （{@code @TableName/@TableId/@TableField}，供 Mapper 实际读写）。两套注解命名空间独立、互不读取。
 * 主键 {@code id} 为固定单行哨兵（恒为 1，由领域指定，非自增），故用 {@code IdType.INPUT}。
 * 阶段4 统一移除 JPA 注解。</p>
 */
@TableName("affinity_settings")
public class AffinitySettingsPO {

    /** 单例 id（恒为 1）。 */
    @TableId(value = "id", type = IdType.INPUT)
    private int id;

    @TableField("enabled")
    private boolean enabled;

    @TableField("switch_on_success")
    private boolean switchOnSuccess;

    @TableField("max_entries")
    private int maxEntries;

    @TableField("default_ttl_seconds")
    private long defaultTtlSeconds;

    @TableField("updated_time")
    private Long updatedTime;

    public AffinitySettingsPO() {
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
