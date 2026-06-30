package com.nexa.infrastructure.modelgroup.persistence;

import com.nexa.infrastructure.modelgroup.persistence.mapper.ModelGroupAccessMapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexa.domain.modelgroup.model.ModelGroupAccess;
import com.nexa.domain.modelgroup.repository.ModelGroupAccessRepository;
import com.nexa.domain.modelgroup.vo.AccessSubjectType;
import com.nexa.infrastructure.modelgroup.persistence.po.ModelGroupAccessPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link ModelGroupAccessRepository} 的 MyBatis-Plus 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link ModelGroupAccessMapper} + PO 就近工厂映射实现。
 * 授权关系无软删除：撤销授权即物理删除（{@code deleteById} / {@code delete(wrapper)}）。</p>
 */
@Repository
public class ModelGroupAccessRepositoryImpl implements ModelGroupAccessRepository {

    private final ModelGroupAccessMapper mapper;

    /**
     * @param mapper 模型组访问授权 MyBatis-Plus Mapper（infra 内部依赖）
     */
    public ModelGroupAccessRepositoryImpl(ModelGroupAccessMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public ModelGroupAccess save(ModelGroupAccess access) {
        ModelGroupAccessPO po = ModelGroupAccessPO.of(access);
        if (po.getId() == null) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
        access.assignId(po.getId());
        return po.toDomain();
    }

    /** {@inheritDoc} */
    @Override
    public boolean deleteById(long id) {
        // 物理删除，受影响行数 0 表示不存在（等价原 existsById 判空 + deleteById）。
        return mapper.deleteById(id) > 0;
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelGroupAccess> findByModelGroupId(long modelGroupId) {
        return mapper.selectList(Wrappers.<ModelGroupAccessPO>lambdaQuery()
                        .eq(ModelGroupAccessPO::getModelGroupId, modelGroupId)).stream()
                .map(ModelGroupAccessPO::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<Long> findGroupIdsBySubject(AccessSubjectType subjectType, long subjectId) {
        return mapper.findGroupIdsBySubject(subjectType.wireValue(), subjectId);
    }

    /** {@inheritDoc} */
    @Override
    public boolean exists(long modelGroupId, AccessSubjectType subjectType, long subjectId) {
        return mapper.selectCount(Wrappers.<ModelGroupAccessPO>lambdaQuery()
                .eq(ModelGroupAccessPO::getModelGroupId, modelGroupId)
                .eq(ModelGroupAccessPO::getSubjectType, subjectType.wireValue())
                .eq(ModelGroupAccessPO::getSubjectId, subjectId)) > 0;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ModelGroupAccess> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(ModelGroupAccessPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public boolean delete(long modelGroupId, AccessSubjectType subjectType, long subjectId) {
        // 物理删除指定主体对指定模型组的授权，受影响行数 > 0 表示确有删除（等价原派生 deleteBy…）。
        int affected = mapper.delete(Wrappers.<ModelGroupAccessPO>lambdaQuery()
                .eq(ModelGroupAccessPO::getModelGroupId, modelGroupId)
                .eq(ModelGroupAccessPO::getSubjectType, subjectType.wireValue())
                .eq(ModelGroupAccessPO::getSubjectId, subjectId));
        return affected > 0;
    }
}
