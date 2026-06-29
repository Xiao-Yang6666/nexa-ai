package com.nexa.infrastructure.billing.persistence;

import com.nexa.domain.billing.model.BalanceTransaction;
import com.nexa.domain.billing.repository.BalanceTransactionRepository;
import com.nexa.domain.billing.vo.BalanceTransactionType;
import com.nexa.infrastructure.billing.persistence.po.BalanceTransactionPO;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 账变流水仓储 {@link BalanceTransactionRepository} 的 JPA 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置：domain 定接口，本类用 {@link SpringDataBalanceTransactionJpaRepository} +
 * 实体↔领域映射实现。账变为不可变历史事实，仅 save（新增）+ findByUser（查）。</p>
 */
@Repository
public class BalanceTransactionRepositoryImpl implements BalanceTransactionRepository {

    private final SpringDataBalanceTransactionJpaRepository jpa;

    /** @param jpa Spring Data JPA 仓库（infra 内部依赖） */
    public BalanceTransactionRepositoryImpl(SpringDataBalanceTransactionJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public BalanceTransaction save(BalanceTransaction tx) {
        BalanceTransactionPO saved = jpa.save(toEntity(tx));
        tx.assignId(saved.getId());
        return toDomain(saved);
    }

    /** {@inheritDoc} */
    @Override
    public List<BalanceTransaction> findByUser(long userId, int limit) {
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, 500);
        return jpa.findByUserIdOrderByCreatedTimeDesc(userId, PageRequest.of(0, safeLimit))
                .stream().map(this::toDomain).toList();
    }

    // ---- 领域 <-> 实体映射 ----

    private BalanceTransactionPO toEntity(BalanceTransaction t) {
        BalanceTransactionPO e = new BalanceTransactionPO();
        e.setId(t.id());
        e.setUserId(t.userId());
        e.setType(t.type().wireValue());
        e.setAmount(t.amount());
        e.setBalanceAfter(t.balanceAfter());
        e.setOperatorId(t.operatorId());
        e.setRemark(t.remark());
        e.setCreatedTime(t.createdTime());
        return e;
    }

    private BalanceTransaction toDomain(BalanceTransactionPO e) {
        return BalanceTransaction.rehydrate(
                e.getId(),
                e.getUserId() == null ? 0L : e.getUserId(),
                BalanceTransactionType.fromWire(e.getType()),
                e.getAmount() == null ? 0L : e.getAmount(),
                e.getBalanceAfter() == null ? 0L : e.getBalanceAfter(),
                e.getOperatorId(),
                e.getRemark(),
                e.getCreatedTime() == null ? 0L : e.getCreatedTime());
    }
}
