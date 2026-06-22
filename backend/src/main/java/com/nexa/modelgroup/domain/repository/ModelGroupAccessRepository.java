package com.nexa.modelgroup.domain.repository;

import com.nexa.modelgroup.domain.model.ModelGroupAccess;
import com.nexa.modelgroup.domain.vo.AccessSubjectType;

import java.util.List;
import java.util.Optional;

/**
 * 模型组访问授权仓储接口（领域层定义，基础设施层实现）。
 *
 * <p>承载私有模型组授权记录的持久化能力。中继链路解析「某令牌可访问哪些私有组」时，按
 * 令牌级 + 其归属用户级两个维度查授权，合并得到可访问模型组 id 集合。</p>
 */
public interface ModelGroupAccessRepository {

    /**
     * 持久化授权记录（授权动作）。新建保存后回填自增 id。
     *
     * @param access 待保存的授权记录
     * @return 持久化后的授权记录（含 id）
     */
    ModelGroupAccess save(ModelGroupAccess access);

    /**
     * 按 id 删除授权记录（撤销授权）。物理删除（授权关系无保留历史需求）。
     *
     * @param id 授权记录主键
     * @return 命中并删除返回 {@code true}；不存在返回 {@code false}
     */
    boolean deleteById(long id);

    /**
     * 列出某模型组的全部授权记录（管理端查看授权清单）。
     *
     * @param modelGroupId 模型组主键
     * @return 授权记录列表
     */
    List<ModelGroupAccess> findByModelGroupId(long modelGroupId);

    /**
     * 按主体查其被授权访问的模型组 id 列表（中继链路解析私有组可见性）。
     *
     * @param subjectType 主体类型（USER/TOKEN）
     * @param subjectId   主体主键
     * @return 该主体被授权的模型组 id 列表
     */
    List<Long> findGroupIdsBySubject(AccessSubjectType subjectType, long subjectId);

    /**
     * 授权重复探测（避免同一主体对同一组重复授权）。
     *
     * @param modelGroupId 模型组主键
     * @param subjectType  主体类型
     * @param subjectId    主体主键
     * @return 已存在返回 {@code true}
     */
    boolean exists(long modelGroupId, AccessSubjectType subjectType, long subjectId);

    /**
     * 按 id 查授权记录（撤销前定位）。
     *
     * @param id 授权记录主键
     * @return 命中返回记录，否则空
     */
    Optional<ModelGroupAccess> findById(long id);
}
