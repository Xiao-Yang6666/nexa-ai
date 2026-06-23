package com.nexa.model.infrastructure.persistence;

import com.nexa.model.domain.model.PlatformModelMapping;
import com.nexa.model.domain.repository.PlatformModelMappingRepository;
import com.nexa.model.domain.vo.Pagination;
import com.nexa.model.infrastructure.persistence.entity.PlatformModelMappingJpaEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link PlatformModelMappingRepository} 的 JPA 实现（基础设施层适配器，F-6002）。
 *
 * <p>DDD 依赖倒置落地。<b>B 不可见数据层闸</b>：本类仅 admin/root 应用层调用，无客户路径。
 * 软删除用 deleted_at。</p>
 */
@Repository("modelPlatformModelMappingRepositoryImpl")
public class PlatformModelMappingRepositoryImpl implements PlatformModelMappingRepository {

    private final SpringDataPlatformModelMappingJpaRepository jpa;

    /** @param jpa Spring Data JPA 仓库（infra 内部依赖） */
    public PlatformModelMappingRepositoryImpl(SpringDataPlatformModelMappingJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public PlatformModelMapping save(PlatformModelMapping mapping) {
        PlatformModelMappingJpaEntity saved = jpa.save(toEntity(mapping));
        mapping.assignId(saved.getId());
        return toDomain(saved);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PlatformModelMapping> findById(long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PlatformModelMapping> findByPublicName(String publicName) {
        if (publicName == null || publicName.isBlank()) {
            return Optional.empty();
        }
        return jpa.findByPublicName(publicName.trim()).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<PlatformModelMapping> findPage(Pagination pagination) {
        Pageable pageable = PageRequest.of(pagination.page() - 1, pagination.pageSize());
        return jpa.findPageOrdered(pageable).stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public long count() {
        return jpa.count();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteById(long id) {
        jpa.softDeleteById(id, Instant.now().getEpochSecond());
    }

    // ---- 领域聚合 <-> JPA 实体映射 ----

    private PlatformModelMappingJpaEntity toEntity(PlatformModelMapping m) {
        PlatformModelMappingJpaEntity e = new PlatformModelMappingJpaEntity();
        e.setId(m.id());
        e.setPublicName(m.publicName());
        e.setUpstreamName(m.upstreamName());
        e.setEnabled(m.enabled());
        e.setRemark(m.remark());
        e.setCreatedTime(m.createdTime());
        e.setUpdatedTime(m.updatedTime());
        return e;
    }

    private PlatformModelMapping toDomain(PlatformModelMappingJpaEntity e) {
        return PlatformModelMapping.builder()
                .id(e.getId())
                .publicName(e.getPublicName())
                .upstreamName(e.getUpstreamName())
                .enabled(e.getEnabled())
                .remark(e.getRemark())
                .createdTime(e.getCreatedTime())
                .updatedTime(e.getUpdatedTime())
                .build();
    }
}
