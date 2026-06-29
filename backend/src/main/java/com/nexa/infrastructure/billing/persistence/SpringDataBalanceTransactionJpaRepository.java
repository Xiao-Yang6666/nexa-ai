package com.nexa.infrastructure.billing.persistence;

import com.nexa.infrastructure.billing.persistence.entity.BalanceTransactionJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 账变流水 Spring Data JPA 仓库（基础设施层内部接口）。
 *
 * <p>仅供 {@link BalanceTransactionRepositoryImpl} 内部使用，领域只认
 * {@code domain.repository.BalanceTransactionRepository}。</p>
 */
interface SpringDataBalanceTransactionJpaRepository extends JpaRepository<BalanceTransactionJpaEntity, Long> {

    /**
     * 按用户查账变（created_time 倒序，分页取上限）。
     *
     * @param userId   用户 id
     * @param pageable 分页（用于 limit）
     * @return 该用户账变实体（时间倒序）
     */
    List<BalanceTransactionJpaEntity> findByUserIdOrderByCreatedTimeDesc(Long userId, Pageable pageable);
}
