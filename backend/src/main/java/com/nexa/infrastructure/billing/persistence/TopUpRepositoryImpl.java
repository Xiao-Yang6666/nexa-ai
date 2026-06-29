package com.nexa.infrastructure.billing.persistence;

import com.nexa.domain.billing.model.TopUp;
import com.nexa.domain.billing.repository.TopUpRepository;
import com.nexa.domain.billing.vo.Money;
import com.nexa.domain.billing.vo.PaymentStatus;
import com.nexa.domain.billing.vo.Quota;
import com.nexa.infrastructure.billing.persistence.po.TopUpPO;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 领域仓储 {@link TopUpRepository} 的 JPA 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link SpringDataTopUpJpaRepository} +
 * 聚合↔实体映射实现（backend-engineer §2.3）。领域聚合 {@link TopUp} 与 JPA 实体分离，
 * 映射集中于此，domain 不感知 Hibernate。{@code tradeNo} 为幂等键，回调按订单号定位订单。</p>
 *
 * <p>可见性：{@code money}（真实货币支付金额）为内部财务字段，仅在 admin/root 管理端可见，
 * 本类只做持久化映射，不构造任何面向客户的读路径。</p>
 */
@Repository
public class TopUpRepositoryImpl implements TopUpRepository {

    private final SpringDataTopUpJpaRepository jpa;

    /**
     * @param jpa Spring Data JPA 仓库（infra 内部依赖）
     */
    public TopUpRepositoryImpl(SpringDataTopUpJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<TopUp> findByTradeNo(String tradeNo) {
        return jpa.findByTradeNo(tradeNo).map(TopUpRepositoryImpl::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public TopUp save(TopUp topUp) {
        TopUpPO saved = jpa.save(toEntity(topUp));
        topUp.assignId(saved.getId());
        return toDomain(saved);
    }

    // ---- 聚合 <-> 实体映射（基础设施层内部，领域不可见） ----

    /**
     * 领域聚合 → JPA 实体（持久化方向）。
     *
     * @param t 充值订单聚合
     * @return 待持久化的 JPA 实体
     */
    private static TopUpPO toEntity(TopUp t) {
        TopUpPO e = new TopUpPO();
        e.setId(t.id());
        e.setUserId(t.userId());
        e.setAmount(t.amount() == null ? null : t.amount().value());
        e.setMoney(t.money() == null ? null : t.money().value());
        e.setTradeNo(t.tradeNo());
        e.setPaymentMethod(t.paymentMethod());
        e.setPaymentProvider(t.paymentProvider());
        e.setStatus(t.status() == null ? PaymentStatus.PENDING.code() : t.status().code());
        e.setCreateTime(t.createTime());
        e.setCompleteTime(t.completeTime());
        return e;
    }

    /**
     * JPA 实体 → 领域聚合（重建方向，走 {@link TopUp#rehydrate}）。
     *
     * @param e JPA 实体
     * @return 重建的充值订单聚合
     */
    private static TopUp toDomain(TopUpPO e) {
        // 值对象构造期的空值兜底（Quota.of/Money.of）留在此处，状态/字段装配走 Builder 具名链式。
        return TopUp.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .amount(Quota.of(e.getAmount() == null ? 0L : e.getAmount()))
                .money(Money.of(e.getMoney() == null ? BigDecimal.ZERO : e.getMoney()))
                .tradeNo(e.getTradeNo())
                .paymentMethod(e.getPaymentMethod())
                .paymentProvider(e.getPaymentProvider())
                .createTime(e.getCreateTime())
                .completeTime(e.getCompleteTime())
                .status(PaymentStatus.fromCode(e.getStatus() == null ? PaymentStatus.PENDING.code() : e.getStatus()))
                .build();
    }
}
