package com.nexa.model.infrastructure.persistence;

import com.nexa.shared.persistence.PageQueries;

import com.nexa.model.domain.model.PublicModel;
import com.nexa.model.domain.repository.PublicModelRepository;
import com.nexa.model.domain.vo.Pagination;
import com.nexa.model.infrastructure.persistence.entity.PublicModelJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link PublicModelRepository} 的 JPA 实现（基础设施层适配器，F-6001/F-6004）。
 *
 * <p>DDD 依赖倒置落地：domain 定接口，本类用 {@link SpringDataPublicModelJpaRepository} + 实体↔领域映射实现。
 * 领域聚合 {@link PublicModel} 与 {@link PublicModelJpaEntity} 分离（domain 不感知 Hibernate）。
 * 软删除用 deleted_at。</p>
 */
@Repository
public class PublicModelRepositoryImpl implements PublicModelRepository {

    private final SpringDataPublicModelJpaRepository jpa;

    /** @param jpa Spring Data JPA 仓库（infra 内部依赖） */
    public PublicModelRepositoryImpl(SpringDataPublicModelJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public PublicModel save(PublicModel model) {
        PublicModelJpaEntity saved = jpa.save(toEntity(model));
        model.assignId(saved.getId());
        return toDomain(saved);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PublicModel> findById(long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PublicModel> findByPublicName(String publicName) {
        if (publicName == null || publicName.isBlank()) {
            return Optional.empty();
        }
        return jpa.findByPublicName(publicName.trim()).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<PublicModel> findPage(Pagination pagination, boolean enabledOnly) {
        Pageable pageable = PageQueries.of(pagination.page(), pagination.pageSize());
        List<PublicModelJpaEntity> rows = enabledOnly
                ? jpa.findEnabledPageOrdered(pageable)
                : jpa.findPageOrdered(pageable);
        return rows.stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public long count(boolean enabledOnly) {
        return enabledOnly ? jpa.countEnabled() : jpa.count();
    }

    /** {@inheritDoc} */
    @Override
    public List<String> findEnabledNames() {
        return jpa.findEnabledPublicNames();
    }

    /** {@inheritDoc} */
    @Override
    public List<PublicModel> findAllEnabled() {
        return jpa.findAllEnabledOrdered().stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteById(long id) {
        jpa.softDeleteById(id, Instant.now().getEpochSecond());
    }

    // ---- 领域聚合 <-> JPA 实体映射 ----

    private PublicModelJpaEntity toEntity(PublicModel m) {
        PublicModelJpaEntity e = new PublicModelJpaEntity();
        e.setId(m.id());
        e.setPublicName(m.publicName());
        e.setBasePriceRatio(m.basePriceRatio());
        e.setUsePrice(m.usePrice());
        e.setBasePrice(m.basePrice());
        e.setEnabled(m.enabled());
        e.setDisplayName(m.displayName());
        e.setSortOrder(m.sortOrder());
        e.setDescription(m.description());
        e.setCreatedTime(m.createdTime());
        e.setUpdatedTime(m.updatedTime());
        return e;
    }

    private PublicModel toDomain(PublicModelJpaEntity e) {
        return PublicModel.builder()
                .id(e.getId())
                .publicName(e.getPublicName())
                .basePriceRatio(e.getBasePriceRatio())
                .usePrice(e.getUsePrice())
                .basePrice(e.getBasePrice())
                .enabled(e.getEnabled())
                .displayName(e.getDisplayName())
                .sortOrder(e.getSortOrder())
                .description(e.getDescription())
                .createdTime(e.getCreatedTime())
                .updatedTime(e.getUpdatedTime())
                .build();
    }
}
