package com.nexa.infrastructure.billing.persistence;

import com.nexa.infrastructure.billing.persistence.entity.RedemptionJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 兑换码 Spring Data JPA 仓库（基础设施层内部接口）。
 *
 * <p>仅供 {@link RedemptionRepositoryImpl} 内部使用，不暴露给应用/领域层——领域只认
 * {@code domain.repository.RedemptionRepository}。</p>
 */
interface SpringDataRedemptionJpaRepository extends JpaRepository<RedemptionJpaEntity, Long> {

    /**
     * 按明文 Key 查实体。
     *
     * @param key 兑换码明文
     * @return 命中实体，否则空
     */
    Optional<RedemptionJpaEntity> findByKey(String key);

    /**
     * 全量分页查询（管理端列表，{@code @SQLRestriction} 自动过滤软删除行）。
     *
     * @param pageable 分页参数
     * @return 当页实体
     */
    Page<RedemptionJpaEntity> findAllBy(Pageable pageable);
}
