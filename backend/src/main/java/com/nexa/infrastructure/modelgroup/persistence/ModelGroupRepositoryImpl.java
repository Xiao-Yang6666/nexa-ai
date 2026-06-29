package com.nexa.infrastructure.modelgroup.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.domain.modelgroup.exception.ModelGroupPersistenceException;
import com.nexa.domain.modelgroup.model.ModelGroup;
import com.nexa.domain.modelgroup.repository.ModelGroupRepository;
import com.nexa.domain.modelgroup.vo.AccessPolicy;
import com.nexa.domain.modelgroup.vo.ModelGroupStatus;
import com.nexa.domain.modelgroup.vo.ModelNames;
import com.nexa.infrastructure.modelgroup.persistence.entity.ModelGroupJpaEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link ModelGroupRepository} 的 JPA 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link SpringDataModelGroupJpaRepository} + 聚合↔实体
 * 映射实现。领域聚合 {@link ModelGroup} 与 JPA 实体分离，映射集中于此，domain 不感知 Hibernate/Jackson。
 * {@code models} 模型集 ⇄ JSONB 用 {@link ObjectMapper} 互转，序列化失败包装为
 * {@link ModelGroupPersistenceException}（不吞错）。</p>
 */
@Repository
public class ModelGroupRepositoryImpl implements ModelGroupRepository {

    private final SpringDataModelGroupJpaRepository jpa;
    private final ObjectMapper objectMapper;

    /**
     * @param jpa          Spring Data JPA 仓库（infra 内部依赖）
     * @param objectMapper Jackson 序列化器（容器提供，复用全站配置）
     */
    public ModelGroupRepositoryImpl(SpringDataModelGroupJpaRepository jpa, ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ModelGroup> findById(long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ModelGroup> findByCode(String code) {
        return jpa.findByCode(code).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelGroup> findAll() {
        return jpa.findAllByOrderByIdAsc().stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelGroup> findByAccessPolicy(AccessPolicy accessPolicy) {
        return jpa.findByAccessPolicyOrderByIdAsc(accessPolicy.wireValue())
                .stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelGroup> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jpa.findByIdIn(ids).stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public boolean existsByCode(String code, Long excludeId) {
        if (excludeId == null) {
            return jpa.existsByCode(code);
        }
        return jpa.existsByCodeAndIdNot(code, excludeId);
    }

    /** {@inheritDoc} */
    @Override
    public ModelGroup save(ModelGroup group) {
        ModelGroupJpaEntity saved = jpa.save(toEntity(group));
        group.assignId(saved.getId());
        return toDomain(saved);
    }

    /** {@inheritDoc} */
    @Override
    public boolean softDelete(long id, long nowEpochSec) {
        Optional<ModelGroupJpaEntity> existing = jpa.findById(id);
        if (existing.isEmpty()) {
            return false;
        }
        ModelGroupJpaEntity entity = existing.get();
        entity.setDeletedAt(nowEpochSec);
        jpa.save(entity);
        return true;
    }

    // ---- 聚合 <-> 实体映射（基础设施层内部，领域不可见） ----

    private ModelGroupJpaEntity toEntity(ModelGroup g) {
        ModelGroupJpaEntity e = new ModelGroupJpaEntity();
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

    private ModelGroup toDomain(ModelGroupJpaEntity e) {
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
