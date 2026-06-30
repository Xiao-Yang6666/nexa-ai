package com.nexa.infrastructure.modelgroup.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.domain.modelgroup.exception.ModelGroupPersistenceException;
import com.nexa.domain.modelgroup.model.ModelGroup;
import com.nexa.domain.modelgroup.repository.ModelGroupRepository;
import com.nexa.domain.modelgroup.vo.AccessPolicy;
import com.nexa.domain.modelgroup.vo.ModelGroupStatus;
import com.nexa.domain.modelgroup.vo.ModelNames;
import com.nexa.infrastructure.modelgroup.persistence.po.ModelGroupPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link ModelGroupRepository} 的 MyBatis-Plus 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link ModelGroupMapper} + 聚合↔PO 映射实现。
 * 领域聚合 {@link ModelGroup} 与 PO {@link ModelGroupPO} 分离，映射集中于此，domain 不感知 MyBatis/Jackson。
 * {@code models} 模型集 ⇄ JSONB 用 {@link ObjectMapper} 互转（序列化失败包装为
 * {@link ModelGroupPersistenceException}，不吞错），故映射保留在本类而非 PO 工厂；jsonb 列读写由 PO 上的
 * {@code JsonbStringTypeHandler} + {@code autoResultMap} 承载。软删除过滤由 PO 的 {@code @TableLogic}
 * 在 select 自动追加 {@code deleted_at IS NULL}，软删除写走 {@link ModelGroupMapper#softDeleteById}。</p>
 */
@Repository
public class ModelGroupRepositoryImpl implements ModelGroupRepository {

    private final ModelGroupMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * @param mapper       模型组 MyBatis-Plus Mapper（infra 内部依赖）
     * @param objectMapper Jackson 序列化器（容器提供，复用全站配置）
     */
    public ModelGroupRepositoryImpl(ModelGroupMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ModelGroup> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ModelGroup> findByCode(String code) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ModelGroupPO>lambdaQuery()
                        .eq(ModelGroupPO::getCode, code)))
                .map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelGroup> findAll() {
        return mapper.selectList(Wrappers.<ModelGroupPO>lambdaQuery()
                        .orderByAsc(ModelGroupPO::getId)).stream()
                .map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelGroup> findByAccessPolicy(AccessPolicy accessPolicy) {
        return mapper.selectList(Wrappers.<ModelGroupPO>lambdaQuery()
                        .eq(ModelGroupPO::getAccessPolicy, accessPolicy.wireValue())
                        .orderByAsc(ModelGroupPO::getId)).stream()
                .map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelGroup> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<ModelGroupPO>lambdaQuery()
                        .in(ModelGroupPO::getId, ids)).stream()
                .map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public boolean existsByCode(String code, Long excludeId) {
        LambdaQueryWrapper<ModelGroupPO> w = Wrappers.<ModelGroupPO>lambdaQuery()
                .eq(ModelGroupPO::getCode, code);
        if (excludeId != null) {
            w.ne(ModelGroupPO::getId, excludeId);
        }
        return mapper.selectCount(w) > 0;
    }

    /** {@inheritDoc} */
    @Override
    public ModelGroup save(ModelGroup group) {
        ModelGroupPO po = toEntity(group);
        if (po.getId() == null) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
        group.assignId(po.getId());
        return toDomain(po);
    }

    /** {@inheritDoc} */
    @Override
    public boolean softDelete(long id, long nowEpochSec) {
        // 软删除写：UPDATE ... SET deleted_at WHERE id AND deleted_at IS NULL。
        // 受影响行数 0 表示不存在或已删（等价原 findById.isEmpty() 分支返回 false）。
        return mapper.softDeleteById(id, nowEpochSec) > 0;
    }

    // ---- 聚合 <-> PO 映射（基础设施层内部，领域不可见；含 JSON 异常翻译，故保留在 Impl） ----

    private ModelGroupPO toEntity(ModelGroup g) {
        ModelGroupPO e = new ModelGroupPO();
        e.setId(g.id());
        e.setName(g.name());
        e.setCode(g.code());
        e.setBasePriceRatio(g.basePriceRatio().value());
        e.setModels(serializeModels(g.models()));
        e.setAccessPolicy(g.accessPolicy().wireValue());
        e.setStatus(g.status().code());
        e.setDescription(g.description());
        e.setCreatedTime(g.createdTime());
        e.setUpdatedTime(g.updatedTime());
        return e;
    }

    private ModelGroup toDomain(ModelGroupPO e) {
        return ModelGroup.builder()
                .id(e.getId())
                .name(e.getName())
                .code(e.getCode())
                .basePriceRatio(e.getBasePriceRatio())
                .models(deserializeModels(e.getModels()))
                .accessPolicy(AccessPolicy.fromWire(e.getAccessPolicy()))
                .status(ModelGroupStatus.fromCode(e.getStatus()))
                .description(e.getDescription())
                .createdTime(e.getCreatedTime())
                .updatedTime(e.getUpdatedTime())
                .build();
    }

    private String serializeModels(ModelNames models) {
        try {
            List<String> values = (models == null) ? List.of() : models.values();
            return objectMapper.writeValueAsString(values);
        } catch (Exception ex) {
            throw new ModelGroupPersistenceException("serialize model group models to JSON failed", ex);
        }
    }

    private ModelNames deserializeModels(String json) {
        if (json == null || json.isBlank()) {
            return ModelNames.EMPTY;
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return ModelNames.of(values);
        } catch (Exception ex) {
            throw new ModelGroupPersistenceException("deserialize model group models from JSON failed", ex);
        }
    }
}
