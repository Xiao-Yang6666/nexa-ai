package com.nexa.infrastructure.modelgroup.persistence;

import com.nexa.domain.modelgroup.model.ModelGroupAccess;
import com.nexa.domain.modelgroup.repository.ModelGroupAccessRepository;
import com.nexa.domain.modelgroup.vo.AccessSubjectType;
import com.nexa.infrastructure.modelgroup.persistence.po.ModelGroupAccessPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link ModelGroupAccessRepository} 的 JPA 实现（基础设施层适配器）。
 *
 * <p>授权关系无软删除：撤销授权即物理删除（{@link #deleteById}）。聚合 ⇄ 实体映射集中于此。</p>
 */
@Repository
public class ModelGroupAccessRepositoryImpl implements ModelGroupAccessRepository {

    private final SpringDataModelGroupAccessJpaRepository jpa;

    /**
     * @param jpa Spring Data JPA 仓库（infra 内部依赖）
     */
    public ModelGroupAccessRepositoryImpl(SpringDataModelGroupAccessJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public ModelGroupAccess save(ModelGroupAccess access) {
        ModelGroupAccessPO saved = jpa.save(toEntity(access));
        access.assignId(saved.getId());
        return toDomain(saved);
    }

    /** {@inheritDoc} */
    @Override
    public boolean deleteById(long id) {
        if (!jpa.existsById(id)) {
            return false;
        }
        jpa.deleteById(id);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelGroupAccess> findByModelGroupId(long modelGroupId) {
        return jpa.findByModelGroupId(modelGroupId).stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<Long> findGroupIdsBySubject(AccessSubjectType subjectType, long subjectId) {
        return jpa.findGroupIdsBySubject(subjectType.wireValue(), subjectId);
    }

    /** {@inheritDoc} */
    @Override
    public boolean exists(long modelGroupId, AccessSubjectType subjectType, long subjectId) {
        return jpa.existsByModelGroupIdAndSubjectTypeAndSubjectId(
                modelGroupId, subjectType.wireValue(), subjectId);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ModelGroupAccess> findById(long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public boolean delete(long modelGroupId, AccessSubjectType subjectType, long subjectId) {
        long affected = jpa.deleteByModelGroupIdAndSubjectTypeAndSubjectId(
                modelGroupId, subjectType.wireValue(), subjectId);
        return affected > 0;
    }

    // ---- 聚合 <-> 实体映射 ----

    private ModelGroupAccessPO toEntity(ModelGroupAccess a) {
        ModelGroupAccessPO e = new ModelGroupAccessPO();
        e.setId(a.id());
        e.setModelGroupId(a.modelGroupId());
        e.setSubjectType(a.subjectType().wireValue());
        e.setSubjectId(a.subjectId());
        e.setCreatedTime(a.createdTime());
        return e;
    }

    private ModelGroupAccess toDomain(ModelGroupAccessPO e) {
        return ModelGroupAccess.rehydrate(
                e.getId(),
                e.getModelGroupId(),
                AccessSubjectType.fromWire(e.getSubjectType()),
                e.getSubjectId(),
                e.getCreatedTime());
    }
}
