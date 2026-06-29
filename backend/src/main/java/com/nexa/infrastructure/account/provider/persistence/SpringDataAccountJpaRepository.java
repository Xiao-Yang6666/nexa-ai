package com.nexa.infrastructure.account.provider.persistence;

import com.nexa.infrastructure.account.provider.persistence.po.AccountPO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA 仓库（供应商账号，基础设施层内部接口）。
 *
 * <p>仅供 {@link AccountRepositoryImpl} 内部使用，领域只认 {@code domain.repository.AccountRepository}。
 * platform 可空过滤用 {@code :platform IS NULL OR ...} 惯用法，分页用 {@link Pageable}。</p>
 */
interface SpringDataAccountJpaRepository extends JpaRepository<AccountPO, Long> {

    /**
     * 平台过滤分页查询（platform 可空=不过滤）。
     *
     * @param platform 平台过滤（可空）
     * @param pageable 分页
     * @return 当前页实体
     */
    @Query("""
            SELECT a FROM AccountPO a
            WHERE (:platform IS NULL OR a.platform = :platform)
            ORDER BY a.priority DESC, a.id ASC
            """)
    List<AccountPO> findPage(@Param("platform") String platform, Pageable pageable);

    /**
     * 平台过滤计数（列表 total）。
     *
     * @param platform 平台过滤（可空）
     * @return 总数
     */
    @Query("""
            SELECT COUNT(a) FROM AccountPO a
            WHERE (:platform IS NULL OR a.platform = :platform)
            """)
    long countFiltered(@Param("platform") String platform);

    /**
     * 按平台列出账号。
     *
     * @param platform 平台
     * @return 该平台下实体列表
     */
    List<AccountPO> findByPlatform(String platform);

    /**
     * 列出 ACTIVE 态账号（可调度的初筛，过期/过载由领域聚合 {@code isSchedulable} 终判）。
     *
     * @return ACTIVE 态实体列表
     */
    @Query("SELECT a FROM AccountPO a WHERE a.status = 'active' ORDER BY a.priority DESC, a.id ASC")
    List<AccountPO> findActive();
}
