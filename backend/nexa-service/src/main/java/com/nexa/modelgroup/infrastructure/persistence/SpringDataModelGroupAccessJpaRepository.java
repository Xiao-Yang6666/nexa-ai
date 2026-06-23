package com.nexa.modelgroup.infrastructure.persistence;

import com.nexa.modelgroup.infrastructure.persistence.entity.ModelGroupAccessJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 模型组访问授权 Spring Data JPA 仓库（基础设施层内部接口）。
 *
 * <p>仅供 {@link ModelGroupAccessRepositoryImpl} 内部使用。授权关系无软删除（撤销即物理删除）。</p>
 */
interface SpringDataModelGroupAccessJpaRepository extends JpaRepository<ModelGroupAccessJpaEntity, Long> {

    /** 列出某模型组的全部授权记录。 */
    List<ModelGroupAccessJpaEntity> findByModelGroupId(Long modelGroupId);

    /** 授权重复探测。 */
    boolean existsByModelGroupIdAndSubjectTypeAndSubjectId(
            Long modelGroupId, String subjectType, Long subjectId);

    /** 删除指定主体对指定模型组的授权（返回受影响行数）。 */
    @Modifying
    long deleteByModelGroupIdAndSubjectTypeAndSubjectId(
            Long modelGroupId, String subjectType, Long subjectId);

    /**
     * 按主体查其被授权的模型组 id 列表（中继链路解析私有组可见性）。
     *
     * @param subjectType 主体类型字面量
     * @param subjectId   主体主键
     * @return 模型组 id 列表
     */
    @Query("SELECT a.modelGroupId FROM ModelGroupAccessJpaEntity a "
            + "WHERE a.subjectType = :subjectType AND a.subjectId = :subjectId")
    List<Long> findGroupIdsBySubject(@Param("subjectType") String subjectType,
                                     @Param("subjectId") Long subjectId);
}
