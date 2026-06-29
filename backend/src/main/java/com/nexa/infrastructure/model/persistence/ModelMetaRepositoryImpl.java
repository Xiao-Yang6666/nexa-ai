package com.nexa.infrastructure.model.persistence;

import com.nexa.common.persistence.PageQueries;

import com.nexa.domain.model.model.ModelMeta;
import com.nexa.domain.model.repository.ModelMetaRepository;
import com.nexa.domain.model.vo.Pagination;
import com.nexa.infrastructure.model.persistence.po.ModelMetaPO;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 领域仓储 {@link ModelMetaRepository} 的 JPA 实现（基础设施层适配器，F-3013~F-3021）。
 *
 * <p>DDD 依赖倒置落地：domain 定接口，本类用 {@link SpringDataModelMetaJpaRepository} + 实体↔领域
 * 映射实现。领域聚合 {@link ModelMeta} 与 {@link ModelMetaPO} 分离（backend-engineer §2.3）。
 * 关键词搜索在本层归一为小写空白（搜索 SQL 不感知归一前的脏值）。软删除用 deleted_at。</p>
 */
@Repository
public class ModelMetaRepositoryImpl implements ModelMetaRepository {

    private final SpringDataModelMetaJpaRepository jpa;

    /** @param jpa Spring Data JPA 仓库（infra 内部依赖） */
    public ModelMetaRepositoryImpl(SpringDataModelMetaJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public ModelMeta save(ModelMeta model) {
        ModelMetaPO entity = toEntity(model);
        ModelMetaPO saved = jpa.save(entity);
        model.assignId(saved.getId());
        return toDomain(saved);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ModelMeta> findById(long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ModelMeta> findByModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return Optional.empty();
        }
        return jpa.findByModelName(modelName.trim()).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelMeta> findPage(Pagination pagination) {
        Pageable pageable = PageQueries.of(pagination.page(), pagination.pageSize());
        return jpa.findPageOrdered(pageable).stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public long count() {
        return jpa.count();
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelMeta> search(String keyword, Long vendorId, Pagination pagination) {
        Pageable pageable = PageQueries.of(pagination.page(), pagination.pageSize());
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        return jpa.searchFiltered(kw, vendorId, pageable).stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public long countSearch(String keyword, Long vendorId) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        return jpa.countFiltered(kw, vendorId);
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelMeta> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteById(long id) {
        jpa.softDeleteById(id, Instant.now().getEpochSecond());
    }

    /** {@inheritDoc} */
    @Override
    public Map<Long, Long> countByVendor() {
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : jpa.countGroupByVendor()) {
            // row[0]=vendorId（可空 Long），row[1]=count（Long）。
            Long vendorId = (Long) row[0];
            Long cnt = (Long) row[1];
            result.put(vendorId, cnt);
        }
        return result;
    }

    // ---- 领域聚合 <-> JPA 实体映射 ----

    private ModelMetaPO toEntity(ModelMeta m) {
        ModelMetaPO e = new ModelMetaPO();
        e.setId(m.id());
        e.setModelName(m.modelName());
        e.setStatus(m.status().code());
        e.setDescription(m.description());
        e.setIcon(m.icon());
        e.setTags(m.tags());
        e.setVendorId(m.vendorId());
        e.setEndpoints(m.endpoints());
        e.setNameRule(m.nameRule());
        e.setSyncOfficial(m.syncOfficial());
        e.setCreatedTime(m.createdTime());
        e.setUpdatedTime(m.updatedTime());
        return e;
    }

    private ModelMeta toDomain(ModelMetaPO e) {
        return ModelMeta.builder()
                .id(e.getId())
                .modelName(e.getModelName())
                .status(e.getStatus())
                .description(e.getDescription())
                .icon(e.getIcon())
                .tags(e.getTags())
                .vendorId(e.getVendorId())
                .endpoints(e.getEndpoints())
                .nameRule(e.getNameRule())
                .syncOfficial(e.getSyncOfficial())
                .createdTime(e.getCreatedTime())
                .updatedTime(e.getUpdatedTime())
                .build();
    }
}
