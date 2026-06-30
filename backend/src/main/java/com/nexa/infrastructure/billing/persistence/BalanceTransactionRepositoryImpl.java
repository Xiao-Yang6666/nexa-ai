package com.nexa.infrastructure.billing.persistence;

import com.nexa.infrastructure.billing.persistence.mapper.BalanceTransactionMapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexa.domain.billing.model.BalanceTransaction;
import com.nexa.domain.billing.repository.BalanceTransactionRepository;
import com.nexa.infrastructure.billing.persistence.po.BalanceTransactionPO;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 账变流水仓储 {@link BalanceTransactionRepository} 的 MyBatis-Plus 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置：domain 定接口，本类用 {@link BalanceTransactionMapper} +
 * PO 就近工厂方法（{@code PO.of} / {@code po.toDomain}）实现。账变为不可变历史事实，
 * 仅 save（新增）+ findByUser（查）。</p>
 */
@Repository
public class BalanceTransactionRepositoryImpl implements BalanceTransactionRepository {

    private final BalanceTransactionMapper mapper;

    /** @param mapper MyBatis-Plus Mapper（infra 内部依赖） */
    public BalanceTransactionRepositoryImpl(BalanceTransactionMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public BalanceTransaction save(BalanceTransaction tx) {
        BalanceTransactionPO po = BalanceTransactionPO.of(tx);
        mapper.insert(po);              // 回填自增 id 到 po
        tx.assignId(po.getId());
        return po.toDomain();
    }

    /** {@inheritDoc} */
    @Override
    public List<BalanceTransaction> findByUser(long userId, int limit) {
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, 500);
        // safeLimit 已 clamp 为 [1,500] 可信整数，last("LIMIT n") 无注入风险。
        LambdaQueryWrapper<BalanceTransactionPO> w = Wrappers.<BalanceTransactionPO>lambdaQuery()
                .eq(BalanceTransactionPO::getUserId, userId)
                .orderByDesc(BalanceTransactionPO::getCreatedTime)
                .last("LIMIT " + safeLimit);
        return mapper.selectList(w).stream()
                .map(BalanceTransactionPO::toDomain)
                .toList();
    }
}
