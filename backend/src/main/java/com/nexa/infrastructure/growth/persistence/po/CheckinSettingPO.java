package com.nexa.infrastructure.growth.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 签到配置持久化实体（基础设施层，对齐 V18 {@code checkin_settings}，PRD GR-3 / FC-022）。
 *
 * <p>DB-SCHEMA §12 注：原系统 CheckinSetting 走 Option KV。本实现落库为<b>单行配置表</b>
 * （固定主键 {@link #SINGLETON_ID}=1），语义等价于一组 KV，但避免引入通用 Option 表的额外复杂度，
 * 且天然满足「全局唯一一份签到配置」。与领域值对象 {@link com.nexa.domain.growth.vo.CheckinSetting}
 * 分离，映射在 {@code CheckinSettingRepositoryImpl}。主键由领域指定（单例固定 1），故 MyBatis-Plus 侧
 * {@code @TableId(type = IdType.INPUT)} 不自增，save 即按固定 id upsert 覆盖。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：保留全部 JPA 注解满足 {@code ddl-auto=validate}，新增 MyBatis-Plus 注解供 Mapper 读写。</p>
 */
@TableName("checkin_settings")
public class CheckinSettingPO {

    /** 全局唯一配置行的固定主键（单例行）。 */
    public static final long SINGLETON_ID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("min_quota")
    private Long minQuota;

    @TableField("max_quota")
    private Long maxQuota;

    /** 框架（JPA / MyBatis-Plus）实例化所需的无参构造器。 */
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
