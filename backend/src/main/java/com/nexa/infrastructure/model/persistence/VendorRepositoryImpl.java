package com.nexa.infrastructure.model.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexa.infrastructure.persistence.PageQueries;

import com.nexa.domain.model.model.Vendor;
import com.nexa.domain.model.repository.VendorRepository;
import com.nexa.domain.model.vo.Pagination;
import com.nexa.infrastructure.model.persistence.po.VendorMetaPO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link VendorRepository} 的 MyBatis-Plus 实现（基础设施层适配器，F-3018）。
 *
 * <p>DDD 依赖倒置落地：domain 定接口，本类用 {@link VendorMetaMapper} + PO 就近工厂方法
 * （{@code PO.of} / {@code po.toDomain}）实现。领域聚合 {@link Vendor} 与 {@link VendorMetaPO} 分离
 * （domain 不感知持久化框架，backend-engineer §2.3）。软删除用 deleted_at（与 tokens 等表惯例一致），
 * select 由 {@code @TableLogic} 自动过滤已删行。</p>
 */
@Repository
public class VendorRepositoryImpl implements VendorRepository {

    private final VendorMetaMapper mapper;

    /** @param mapper MyBatis-Plus Mapper（infra 内部依赖） */
    public VendorRepositoryImpl(VendorMetaMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public Vendor save(Vendor vendor) {
        VendorMetaPO entity = VendorMetaPO.of(vendor);
        if (entity.getId() == null) {
            mapper.insert(entity);          // 回填自增 id
        } else {
            mapper.updateById(entity);
        }
        vendor.assignId(entity.getId());
        return entity.toDomain();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Vendor> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(VendorMetaPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Vendor> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<VendorMetaPO> w = Wrappers.<VendorMetaPO>lambdaQuery()
                .eq(VendorMetaPO::getName, name.trim());
        return Optional.ofNullable(mapper.selectOne(w)).map(VendorMetaPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<Vendor> findPage(Pagination pagination) {
        Page<VendorMetaPO> page = PageQueries.mpOf(pagination.page(), pagination.pageSize());
        LambdaQueryWrapper<VendorMetaPO> w = Wrappers.<VendorMetaPO>lambdaQuery()
                .orderByAsc(VendorMetaPO::getId);
        return mapper.selectPage(page, w).getRecords().stream().map(VendorMetaPO::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public long count() {
        return mapper.selectCount(Wrappers.<VendorMetaPO>lambdaQuery());
    }

    /** {@inheritDoc} */
    @Override
    public List<Vendor> search(String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        if (kw.isEmpty()) {
            // 空关键词等价全量（不分页，供应商量级小）。
            return mapper.selectList(Wrappers.<VendorMetaPO>lambdaQuery())
                    .stream().map(VendorMetaPO::toDomain).toList();
        }
        // {0} 占位绑参防注入；列名大小写不敏感包含匹配，与原 JPQL LOWER(name) LIKE %kw% 等价。
        LambdaQueryWrapper<VendorMetaPO> w = Wrappers.<VendorMetaPO>lambdaQuery()
                .apply("LOWER(name) LIKE LOWER({0})", "%" + kw + "%")
                .orderByAsc(VendorMetaPO::getId);
        return mapper.selectList(w).stream().map(VendorMetaPO::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<Vendor> findAll() {
        return mapper.selectList(Wrappers.<VendorMetaPO>lambdaQuery())
                .stream().map(VendorMetaPO::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteById(long id) {
        mapper.softDeleteById(id, Instant.now().getEpochSecond());
    }
}
