package com.nexa.infrastructure.modelgroup.persistence.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 模型组访问授权 JPA 持久化实体（基础设施层，对齐 V27 {@code model_group_access}）。
 *
 * <p>承载私有模型组的授权关系。{@code (model_group_id, subject_type, subject_id)} 三元唯一约束
 * {@code uk_mga_subject} 兜底重复授权；{@code idx_mga_subject} 加速「按主体查可访问组」。授权关系无
 * 软删除需求（撤销即物理删除）。</p>
 */
@Entity
@Table(name = "model_group_access",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mga_subject",
                columnNames = {"model_group_id", "subject_type", "subject_id"}),
        indexes = {
                @Index(name = "idx_mga_subject", columnList = "subject_type,subject_id"),
                @Index(name = "idx_mga_group", columnList = "model_group_id")
        })
public class ModelGroupAccessPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_group_id", nullable = false)
    private Long modelGroupId;

    @Column(name = "subject_type", length = 10, nullable = false)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "created_time")
    private Long createdTime;

    /** JPA 规范要求的无参构造器。 */
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
}
