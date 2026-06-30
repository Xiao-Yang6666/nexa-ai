package com.nexa.infrastructure.billing.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexa.domain.billing.model.Redemption;
import com.nexa.domain.billing.repository.RedemptionRepository;
import com.nexa.infrastructure.billing.persistence.po.RedemptionPO;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link RedemptionRepository} 的 MyBatis-Plus 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link RedemptionMapper} + PO 就近工厂方法
 * （{@code RedemptionPO.of} / {@code po.toDomain}）实现。领域聚合 {@link Redemption} 与 PO 分离，
 * domain 不感知持久化框架。软删除由 PO 的 {@code @TableLogic} 驱动 select 自动过滤。</p>
 */
@Repository
public class RedemptionRepositoryImpl implements RedemptionRepository {

    private final RedemptionMapper mapper;

    /**
     * @param mapper MyBatis-Plus Mapper（infra 内部依赖）
     */
    public RedemptionRepositoryImpl(RedemptionMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Redemption> findByKey(String key) {
        LambdaQueryWrapper<RedemptionPO> w = Wrappers.<RedemptionPO>lambdaQuery()
                .eq(RedemptionPO::getKey, key);
        return Optional.ofNullable(mapper.selectOne(w)).map(RedemptionPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Redemption save(Redemption redemption) {
        RedemptionPO po = RedemptionPO.of(redemption);
        if (po.getId() == null) {
            mapper.insert(po);              // 回填自增 id 到 po
        } else {
            mapper.updateById(po);
        }
        redemption.assignId(po.getId());
        return po.toDomain();
    }

    /** {@inheritDoc} */
    @Override
    public List<Redemption> saveAll(List<Redemption> redemptions) {
        List<Redemption> result = new ArrayList<>(redemptions.size());
        for (Redemption r : redemptions) {
            RedemptionPO po = RedemptionPO.of(r);
            if (po.getId() == null) {
                mapper.insert(po);          // 回填自增 id 到 po
            } else {
                mapper.updateById(po);
            }
            r.assignId(po.getId());
            result.add(po.toDomain());
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public Page<Redemption> findPage(int page, int pageSize) {
        int p = Math.max(page, 1);
        int size = Math.max(pageSize, 1);
        // 管理端列表按 id 降序（最新生成的码在前），结果稳定可分页；@TableLogic 自动过滤软删除行。
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<RedemptionPO> mpPage =
                com.nexa.infrastructure.persistence.PageQueries.mpOf(p, size);
        LambdaQueryWrapper<RedemptionPO> w = Wrappers.<RedemptionPO>lambdaQuery()
                .orderByDesc(RedemptionPO::getId);
        mapper.selectPage(mpPage, w);
        List<Redemption> items = mpPage.getRecords().stream()
                .map(RedemptionPO::toDomain)
                .toList();
        return new Page<>(items, mpPage.getTotal(), p, size);
    }
}
