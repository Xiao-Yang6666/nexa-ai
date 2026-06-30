package com.nexa.infrastructure.model.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexa.domain.model.model.PublicModel;
import com.nexa.domain.model.repository.PublicModelRepository;
import com.nexa.domain.model.vo.Pagination;
import com.nexa.infrastructure.model.persistence.po.PublicModelPO;
import com.nexa.infrastructure.persistence.PageQueries;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link PublicModelRepository} 的 MyBatis-Plus 实现（基础设施层适配器，F-6001/F-6004）。
 *
 * <p>DDD 依赖倒置：domain 定接口，本类用 {@link PublicModelMapper} + PO 就近工厂方法
 * （{@code PO.of} / {@code po.toDomain}）实现。{@code select} 经 {@code @TableLogic} 自动过滤已软删行；
 * 软删除写走 {@link PublicModelMapper#softDeleteById}。</p>
 */
@Repository
public class PublicModelRepositoryImpl implements PublicModelRepository {

    private final PublicModelMapper mapper;

    /** @param mapper MyBatis-Plus Mapper（infra 内部依赖） */
    public PublicModelRepositoryImpl(PublicModelMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public PublicModel save(PublicModel model) {
        PublicModelPO po = PublicModelPO.of(model);
        if (po.getId() == null) {
            mapper.insert(po);              // 回填自增 id
        } else {
            mapper.updateById(po);
        }
        model.assignId(po.getId());
        return po.toDomain();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PublicModel> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(PublicModelPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PublicModel> findByPublicName(String publicName) {
        if (publicName == null || publicName.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<PublicModelPO> w = Wrappers.<PublicModelPO>lambdaQuery()
                .eq(PublicModelPO::getPublicName, publicName.trim());
        return Optional.ofNullable(mapper.selectOne(w)).map(PublicModelPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<PublicModel> findPage(Pagination pagination, boolean enabledOnly) {
        Page<PublicModelPO> page = PageQueries.mpOf(pagination.page(), pagination.pageSize());
        LambdaQueryWrapper<PublicModelPO> w = Wrappers.<PublicModelPO>lambdaQuery()
                .eq(enabledOnly, PublicModelPO::getEnabled, true)
                .orderByAsc(PublicModelPO::getSortOrder)
                .orderByAsc(PublicModelPO::getId);
        return mapper.selectPage(page, w).getRecords().stream()
                .map(PublicModelPO::toDomain)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public long count(boolean enabledOnly) {
        LambdaQueryWrapper<PublicModelPO> w = Wrappers.<PublicModelPO>lambdaQuery()
                .eq(enabledOnly, PublicModelPO::getEnabled, true);
        return mapper.selectCount(w);
    }

    /** {@inheritDoc} */
    @Override
    public List<String> findEnabledNames() {
        return mapper.findEnabledPublicNames();
    }

    /** {@inheritDoc} */
    @Override
    public List<PublicModel> findAllEnabled() {
        LambdaQueryWrapper<PublicModelPO> w = Wrappers.<PublicModelPO>lambdaQuery()
                .eq(PublicModelPO::getEnabled, true)
                .orderByAsc(PublicModelPO::getSortOrder)
                .orderByAsc(PublicModelPO::getId);
        return mapper.selectList(w).stream().map(PublicModelPO::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteById(long id) {
        mapper.softDeleteById(id, Instant.now().getEpochSecond());
    }
}
