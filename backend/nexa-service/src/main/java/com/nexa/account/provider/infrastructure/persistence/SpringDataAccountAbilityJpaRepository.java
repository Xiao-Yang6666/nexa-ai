package com.nexa.account.provider.infrastructure.persistence;

import com.nexa.account.provider.infrastructure.persistence.entity.AccountAbilityJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Ability 路由索引 Spring Data JPA 仓储（账号维度，V33 重建）。
 */
public interface SpringDataAccountAbilityJpaRepository extends JpaRepository<AccountAbilityJpaEntity, Long> {

    /**
     * 删除指定账号的所有 ability 记录（fan-in）。
     *
     * @param accountId 账号 id
     */
    @Modifying
    @Query("DELETE FROM AccountAbilityJpaEntity a WHERE a.accountId = :accountId")
    void deleteByAccountId(@Param("accountId") Long accountId);

    /**
     * 查询指定 group 下 status=ACTIVE 且 models 包含给定模型的 ability 行（CH-2 候选集，账号维度）。
     *
     * <p>models 为逗号分隔串，用 LIKE 粗筛后由调用方精确匹配。</p>
     *
     * @param group 分组
     * @return 该分组下 ACTIVE 的 ability 行
     */
    @Query("SELECT a FROM AccountAbilityJpaEntity a WHERE a.group = :group AND a.status = 'ACTIVE'")
    List<AccountAbilityJpaEntity> findActiveByGroup(@Param("group") String group);
}
