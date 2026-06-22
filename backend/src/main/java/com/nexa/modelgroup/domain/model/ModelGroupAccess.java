package com.nexa.modelgroup.domain.model;

import com.nexa.modelgroup.domain.exception.InvalidModelGroupParameterException;
import com.nexa.modelgroup.domain.vo.AccessSubjectType;

/**
 * 模型组访问授权聚合根（充血领域模型，私有模型组的访问授权一致性边界）。
 *
 * <p>承载「某用户/某令牌被授权访问某私有模型组」这一关系。{@link AccessPolicy#PRIVATE} 的模型组只对
 * 显式授权的主体可见可用；本聚合即该显式授权记录。授权粒度由 {@link AccessSubjectType} 区分（用户级
 * 覆盖该用户名下所有令牌，令牌级仅授权指定令牌，便于按 key 售卖）。</p>
 *
 * <p>零框架依赖，可纯单测。不变量：modelGroupId/subjectId 必为正、subjectType 非空；
 * {@code (modelGroupId, subjectType, subjectId)} 三元组唯一（由仓储/DB 唯一索引兜底，聚合不感知重复）。</p>
 */
public final class ModelGroupAccess {

    private Long id;
    private final long modelGroupId;
    private final AccessSubjectType subjectType;
    private final long subjectId;
    private final Long createdTime;

    private ModelGroupAccess(Long id, long modelGroupId, AccessSubjectType subjectType,
                             long subjectId, Long createdTime) {
        this.id = id;
        this.modelGroupId = modelGroupId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.createdTime = createdTime;
    }

    /**
     * 授权一个主体访问模型组（工厂方法，充血行为，校验不变量）。
     *
     * @param modelGroupId 被授权访问的模型组主键（必 &gt; 0）
     * @param subjectType  授权主体类型（必填）
     * @param subjectId    主体主键（userId 或 tokenId，必 &gt; 0）
     * @param nowEpochSec  当前时间（epoch 秒）
     * @return 待持久化的授权记录（id 由仓储保存后回填）
     * @throws InvalidModelGroupParameterException 字段非法
     */
    public static ModelGroupAccess grant(long modelGroupId, AccessSubjectType subjectType,
                                         long subjectId, long nowEpochSec) {
        if (modelGroupId <= 0) {
            throw new InvalidModelGroupParameterException("modelGroupId must be positive");
        }
        if (subjectType == null) {
            throw new InvalidModelGroupParameterException("access subject type is required");
        }
        if (subjectId <= 0) {
            throw new InvalidModelGroupParameterException("subjectId must be positive");
        }
        return new ModelGroupAccess(null, modelGroupId, subjectType, subjectId, nowEpochSec);
    }

    /**
     * 持久化重建专用工厂：从已存数据装配（不触发授权不变量与时间打点）。
     *
     * @param id           主键
     * @param modelGroupId 模型组主键
     * @param subjectType  主体类型
     * @param subjectId    主体主键
     * @param createdTime  创建时间（epoch 秒）
     * @return 重建的授权记录
     */
    public static ModelGroupAccess rehydrate(Long id, long modelGroupId, AccessSubjectType subjectType,
                                             long subjectId, Long createdTime) {
        return new ModelGroupAccess(id, modelGroupId, subjectType, subjectId, createdTime);
    }

    /**
     * 回填数据库生成的主键（save 后调用）。
     *
     * @param assignedId 数据库自增 id
     */
    public void assignId(Long assignedId) {
        this.id = assignedId;
    }

    // ---- 只读访问器 ----

    /** @return 主键（未持久化为 null） */
    public Long id() {
        return id;
    }

    /** @return 被授权访问的模型组主键 */
    public long modelGroupId() {
        return modelGroupId;
    }

    /** @return 授权主体类型 */
    public AccessSubjectType subjectType() {
        return subjectType;
    }

    /** @return 主体主键（userId 或 tokenId） */
    public long subjectId() {
        return subjectId;
    }

    /** @return 创建时间（epoch 秒） */
    public Long createdTime() {
        return createdTime;
    }
}
