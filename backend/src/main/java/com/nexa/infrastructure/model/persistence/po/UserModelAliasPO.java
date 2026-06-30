package com.nexa.infrastructure.model.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.model.model.UserModelAlias;
import com.nexa.domain.model.vo.AliasScopeType;

/**
 * 客户层自助映射 JPA 持久化实体（基础设施层，对齐 V13 {@code user_model_aliases}，DB-SCHEMA §18）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.model.model.UserModelAlias} 分离，映射由就近工厂方法
 * {@link #toDomain()} / {@link #of(UserModelAlias)} 承载。</p>
 *
 * <p>软删除：{@code deleted_at} 为可空 epoch 秒。MyBatis-Plus 侧 {@code @TableLogic(value = "null")} 让
 * {@code select} 自动追加 {@code deleted_at IS NULL}（等价 JPA {@code @SQLRestriction}）；软删<b>写</b>走 Mapper
 * 显式 {@code @Update}。并存期保留 {@code @SQLRestriction}。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：保留全部 JPA 注解满足 {@code ddl-auto=validate}，新增 MyBatis-Plus 注解供 Mapper 读写。
 * {@code @Entity} 逻辑名 {@code ModelUserModelAliasJpaEntity} 与 relay 同名类区分，沿用不变。</p>
 */
@TableName("user_model_aliases")
public class UserModelAliasPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("scope_type")
    private String scopeType;

    @TableField("scope_id")
    private String scopeId;

    @TableField("alias")
    private String alias;

    @TableField("target")
    private String target;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("created_time")
    private Long createdTime;

    @TableField("updated_time")
    private Long updatedTime;

    @TableField("deleted_at")
    @TableLogic(value = "null")
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

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * 领域聚合 → PO（持久化方向）：逐字段映射，{@code scopeType} 写入枚举 code，无副作用于入参。
     *
     * @param a 客户层映射领域聚合（非空）
     * @return 待持久化的 PO
     */
    public static UserModelAliasPO of(UserModelAlias a) {
        UserModelAliasPO e = new UserModelAliasPO();
        e.id = a.id();
        e.scopeType = a.scopeType().code();
        e.scopeId = a.scopeId();
        e.alias = a.alias();
        e.target = a.target();
        e.enabled = a.enabled();
        e.createdTime = a.createdTime();
        e.updatedTime = a.updatedTime();
        return e;
    }

    /**
     * PO → 领域聚合（重建方向，走 {@link UserModelAlias#builder()}）。{@code scopeType} 经
     * {@link AliasScopeType#fromCode} 解析。
     *
     * @return 重建的客户层映射聚合
     */
    public UserModelAlias toDomain() {
        return UserModelAlias.builder()
                .id(id)
                .scopeType(AliasScopeType.fromCode(scopeType))
                .scopeId(scopeId)
                .alias(alias)
                .target(target)
                .enabled(enabled)
                .createdTime(createdTime)
                .updatedTime(updatedTime)
                .build();
    }
}
