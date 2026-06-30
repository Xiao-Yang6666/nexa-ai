package com.nexa.infrastructure.relay.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.relay.model.UserModelAlias;
import com.nexa.domain.relay.vo.AliasScope;

/**
 * 客户层别名 JPA 持久化实体（基础设施层，对齐 V13 {@code user_model_aliases} 与 DB-SCHEMA §21）。
 *
 * <p>与领域聚合 {@link com.nexa.domain.relay.model.UserModelAlias} 分离。复合唯一键
 * (scope_type, scope_id, alias) 由 V13 唯一索引 {@code uk_scope_alias} 守护（部分索引含 deleted_at IS NULL）。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：本 PO 同时保留 JPA 注解（{@code @Entity/@Table/@Column/@Id}，
 * 满足并存期 {@code ddl-auto=validate} 全局启动校验）与 MyBatis-Plus 注解
 * （{@code @TableName/@TableId/@TableField}，供 Mapper 实际读写）。
 * <b>软删除</b>：本表原无 {@code @SQLRestriction}，删除过滤迁移前由各条 JPQL 显式
 * {@code deleted_at IS NULL} 完成；迁移后由 {@code @TableLogic(value="null")} 让 MyBatis-Plus
 * 的 select 自动追加 {@code deleted_at IS NULL}。软删除写不走 {@code deleteById}（未声明 delval，
 * 与 epoch 秒不符），改由 Mapper 显式 {@code @Update softDeleteById}。两套注解命名空间独立、互不读取。
 * 阶段4 统一移除 JPA 注解。</p>
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
    private boolean enabled = true;

    @TableField("created_time")
    private Long createdTime;

    @TableField("updated_time")
    private Long updatedTime;

    @TableField("deleted_at")
    @TableLogic(value = "null")
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

    // ---- 就近映射工厂方法：把 RepositoryImpl 里的私有 toDomain / save 装配逻辑搬到这里 ----

    /**
     * PO → 领域聚合（重建方向）：scope 值对象由 (scope_type, scope_id) 两列重建，
     * {@code scopeType} 经 {@link AliasScope.ScopeType#fromWire} 解析。
     *
     * @return 重建的别名领域对象
     */
    public UserModelAlias toDomain() {
        AliasScope scope = new AliasScope(AliasScope.ScopeType.fromWire(scopeType), scopeId);
        return UserModelAlias.builder()
                .id(id)
                .scope(scope)
                .alias(alias)
                .target(target)
                .enabled(enabled)
                .createdTime(createdTime)
                .updatedTime(updatedTime)
                .build();
    }

    /**
     * 领域聚合 → PO（持久化方向）：scope 值对象拆为 (scope_type, scope_id) 两列。
     * {@code deletedAt} 不在此设置（软删除写走 Mapper 显式 {@code @Update}）。
     *
     * @param alias 别名领域对象（非空）
     * @return 待持久化的 PO
     */
    public static UserModelAliasPO of(UserModelAlias alias) {
        UserModelAliasPO po = new UserModelAliasPO();
        po.id = alias.id();
        po.scopeType = alias.scope().type().wire();
        po.scopeId = alias.scope().id();
        po.alias = alias.alias();
        po.target = alias.target();
        po.enabled = alias.isEnabled();
        po.createdTime = alias.createdTime();
        po.updatedTime = alias.updatedTime();
        return po;
    }
}
