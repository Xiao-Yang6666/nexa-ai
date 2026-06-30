package com.nexa.infrastructure.modelgroup.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.modelgroup.model.ModelGroupAccess;
import com.nexa.domain.modelgroup.vo.AccessSubjectType;

/**
 * 模型组访问授权持久化实体（基础设施层，对齐 V27 {@code model_group_access}）。
 *
 * <p>承载私有模型组的授权关系。{@code (model_group_id, subject_type, subject_id)} 三元唯一约束
 * {@code uk_mga_subject} 兜底重复授权；{@code idx_mga_subject} 加速「按主体查可访问组」。授权关系无
 * 软删除需求（撤销即物理删除）。映射由本类就近工厂 {@link #toDomain()} / {@link #of(ModelGroupAccess)} 承载，
 * domain 仍零感知 PO。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：保留全部 JPA 注解满足 {@code ddl-auto=validate}，新增 MyBatis-Plus 注解供 Mapper 读写。</p>
 */
@TableName("model_group_access")
public class ModelGroupAccessPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("model_group_id")
    private Long modelGroupId;

    @TableField("subject_type")
    private String subjectType;

    @TableField("subject_id")
    private Long subjectId;

    @TableField("created_time")
    private Long createdTime;

    /** 框架（MyBatis-Plus）实例化所需的无参构造器。 */
    public ModelGroupAccessPO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getModelGroupId() {
        return modelGroupId;
    }

    public void setModelGroupId(Long modelGroupId) {
        this.modelGroupId = modelGroupId;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public Long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Long createdTime) {
        this.createdTime = createdTime;
    }

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * 领域聚合 → PO（持久化方向）。
     *
     * @param a 授权聚合（非空）
     * @return 待持久化的 PO
     */
    public static ModelGroupAccessPO of(ModelGroupAccess a) {
        ModelGroupAccessPO e = new ModelGroupAccessPO();
        e.id = a.id();
        e.modelGroupId = a.modelGroupId();
        e.subjectType = a.subjectType().wireValue();
        e.subjectId = a.subjectId();
        e.createdTime = a.createdTime();
        return e;
    }

    /**
     * PO → 领域聚合（重建方向）。
     *
     * @return 重建的授权聚合
     */
    public ModelGroupAccess toDomain() {
        return ModelGroupAccess.rehydrate(
                id,
                modelGroupId,
                AccessSubjectType.fromWire(subjectType),
                subjectId,
                createdTime);
    }
}
