package com.nexa.infrastructure.billing.persistence;

import com.nexa.infrastructure.billing.persistence.mapper.TopUpMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexa.domain.billing.model.TopUp;
import com.nexa.domain.billing.repository.TopUpRepository;
import com.nexa.infrastructure.billing.persistence.po.TopUpPO;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 领域仓储 {@link TopUpRepository} 的 MyBatis-Plus 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link TopUpMapper} + PO 就近工厂方法
 * （{@code PO.of} / {@code po.toDomain}）实现。领域聚合 {@link TopUp} 与 PO 分离，domain 不感知
 * MyBatis-Plus。{@code tradeNo} 为幂等键，回调按订单号定位订单。</p>
 *
 * <p>可见性：{@code money}（真实货币支付金额）为内部财务字段，仅在 admin/root 管理端可见，
 * 本类只做持久化映射，不构造任何面向客户的读路径。</p>
 */
@Repository
public class TopUpRepositoryImpl implements TopUpRepository {

    private final TopUpMapper mapper;

    /**
     * @param mapper MyBatis-Plus Mapper（infra 内部依赖）
     */
    public TopUpRepositoryImpl(TopUpMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<TopUp> findByTradeNo(String tradeNo) {
        // 商户订单号唯一索引，至多一条；selectOne 命中即重建，否则空。
        LambdaQueryWrapper<TopUpPO> w = Wrappers.<TopUpPO>lambdaQuery()
                .eq(TopUpPO::getTradeNo, tradeNo);
        return Optional.ofNullable(mapper.selectOne(w)).map(TopUpPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public TopUp save(TopUp topUp) {
        TopUpPO po = TopUpPO.of(topUp);
        mapper.insert(po);              // 回填自增 id 到 po
        topUp.assignId(po.getId());
        return po.toDomain();
    }
}
