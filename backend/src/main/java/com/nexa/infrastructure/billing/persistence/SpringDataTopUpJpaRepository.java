package com.nexa.infrastructure.billing.persistence;

import com.nexa.infrastructure.billing.persistence.entity.TopUpJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 充值订单 Spring Data JPA 仓库（基础设施层内部接口）。
 *
 * <p>仅供 {@link TopUpRepositoryImpl} 内部使用，不暴露给应用/领域层——领域只认
 * {@code domain.repository.TopUpRepository}。</p>
 */
interface SpringDataTopUpJpaRepository extends JpaRepository<TopUpJpaEntity, Long> {

    /**
     * 按商户订单号查实体（回调幂等定位）。
     *
     * @param tradeNo 商户订单号（幂等键）
     * @return 命中实体，否则空
     */
    Optional<TopUpJpaEntity> findByTradeNo(String tradeNo);
}
