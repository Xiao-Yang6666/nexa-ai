package com.nexa.infrastructure.model.persistence;

import com.nexa.shared.persistence.PageQueries;

import com.nexa.domain.model.model.Vendor;
import com.nexa.domain.model.repository.VendorRepository;
import com.nexa.domain.model.vo.Pagination;
import com.nexa.infrastructure.model.persistence.entity.VendorMetaJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link VendorRepository} 的 JPA 实现（基础设施层适配器，F-3018）。
 *
 * <p>DDD 依赖倒置落地：domain 定接口，本类用 {@link SpringDataVendorMetaJpaRepository} + 实体↔领域
 * 映射实现。领域聚合 {@link Vendor} 与 {@link VendorMetaJpaEntity} 分离（domain 不感知 Hibernate，
 * backend-engineer §2.3）。软删除用 deleted_at（与 tokens 等表惯例一致）。</p>
 */
@Repository
public class VendorRepositoryImpl implements VendorRepository {

    private final SpringDataVendorMetaJpaRepository jpa;

    /** @param jpa Spring Data JPA 仓库（infra 内部依赖） */
    public VendorRepositoryImpl(SpringDataVendorMetaJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public Vendor save(Vendor vendor) {
        VendorMetaJpaEntity entity = toEntity(vendor);
        VendorMetaJpaEntity saved = jpa.save(entity);
        vendor.assignId(saved.getId());
        return toDomain(saved);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Vendor> findById(long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Vendor> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return jpa.findByName(name.trim()).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<Vendor> findPage(Pagination pagination) {
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
    public List<Vendor> search(String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        if (kw.isEmpty()) {
            // 空关键词等价全量（不分页，供应商量级小）。
            return jpa.findAll().stream().map(this::toDomain).toList();
        }
        return jpa.searchByKeyword(kw).stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<Vendor> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteById(long id) {
        jpa.softDeleteById(id, Instant.now().getEpochSecond());
    }

    // ---- 领域聚合 <-> JPA 实体映射 ----

    private VendorMetaJpaEntity toEntity(Vendor v) {
        VendorMetaJpaEntity e = new VendorMetaJpaEntity();
        e.setId(v.id());
        e.setName(v.name());
        e.setIcon(v.icon());
        e.setStatus(v.status().code());
        e.setCreatedTime(v.createdTime());
        e.setUpdatedTime(v.updatedTime());
        return e;
    }

    private Vendor toDomain(VendorMetaJpaEntity e) {
        return Vendor.rehydrate(e.getId(), e.getName(), e.getIcon(), e.getStatus(),
                e.getCreatedTime(), e.getUpdatedTime());
    }
}
