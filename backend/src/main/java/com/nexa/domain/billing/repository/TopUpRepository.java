package com.nexa.domain.billing.repository;

import com.nexa.domain.billing.model.TopUp;

import java.util.Optional;

/**
 * 充值订单聚合仓储接口（领域层定义，基础设施层实现）。
 *
 * <p>DDD 依赖倒置（backend-engineer §2.3）。{@code tradeNo} 为幂等键，回调按订单号定位订单。
 * 实现见 {@code infrastructure.persistence.TopUpRepositoryImpl}。</p>
 */
public interface TopUpRepository {

    /**
     * 按商户订单号查找充值订单（回调幂等定位，prd-billing BL-1 pay_idem）。
     *
     * @param tradeNo 商户订单号（幂等键）
     * @return 命中返回聚合，否则空
     */
    Optional<TopUp> findByTradeNo(String tradeNo);

    /**
     * 持久化充值订单聚合（新增或更新）。
     *
     * @param topUp 待保存的聚合
     * @return 持久化后的聚合（含 id）
     */
    TopUp save(TopUp topUp);
}
