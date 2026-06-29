package com.nexa.infrastructure.modelgroup.persistence;

import com.nexa.infrastructure.modelgroup.persistence.entity.ModelGroupJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 模型组 Spring Data JPA 仓库（基础设施层内部接口）。
 *
 * <p>仅供 {@link ModelGroupRepositoryImpl} 内部使用——领域只认 {@code domain.repository.ModelGroupRepository}。
 * 所有查询经 {@code @SQLRestriction} 自动过滤软删除行（code/policy 派生查询同理只命中存活行）。</p>
 */
interface SpringDataModelGroupJpaRepository extends JpaRepository<ModelGroupJpaEntity, Long> {

    /** 全量按 id 升序查（管理端列表）。 */
    List<ModelGroupJpaEntity> findAllByOrderByIdAsc();

    /** 按 code 查存活模型组（中继选组）。 */
    Optional<ModelGroupJpaEntity> findByCode(String code);

    /** 按访问策略查，id 升序。 */
    List<ModelGroupJpaEntity> findByAccessPolicyOrderByIdAsc(String accessPolicy);

    /** 按 id 集合批量查存活模型组。 */
    List<ModelGroupJpaEntity> findByIdIn(List<Long> ids);

    /** code 冲突探测（创建场景，无自身排除）。 */
    boolean existsByCode(String code);

    /** code 冲突探测（更新场景，排除自身 id）。 */
    boolean existsByCodeAndIdNot(String code, Long id);

    /** 按 id 查存活实体（更新/软删除定位）。 */
    Optional<ModelGroupJpaEntity> findById(Long id);
}
