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
     * <p>models 为逗号分隔串，用 LIKE 粗筛后由调用方精确匹配。status 持久化为小写
     * （{@code AccountStatus.ACTIVE.code() == "active"}），故此处匹配小写 {@code 'active'}。</p>
     *
     * @param group 分组
     * @return 该分组下 ACTIVE 的 ability 行
     */
    @Query("SELECT a FROM AccountAbilityJpaEntity a WHERE a.group = :group AND a.status = 'active'")
    List<AccountAbilityJpaEntity> findActiveByGroup(@Param("group") String group);

    /**
     * 按模型粗筛 status=active 的 ability 行（方案乙：选账号按模型 A 反查，跨全部分组）。
     *
     * <p>models 为逗号分隔串，用 LIKE {@code %model%} 粗筛（可能误命中子串，如 "gpt-4" 命中 "gpt-4o"），
     * 故调用方<b>必须</b>用账号聚合 {@code models().contains(A)} 做精确包含再筛一遍。accountId 去重与精确
     * 匹配由 {@code AccountRepositoryImpl} 负责。status 持久化为小写（{@code AccountStatus.code()}），
     * 故匹配小写 {@code 'active'}。</p>
     *
     * @param modelLike 形如 {@code %模型名%} 的 LIKE 模式
     * @return 粗筛命中的 ACTIVE ability 行
     */
    @Query("SELECT a FROM AccountAbilityJpaEntity a WHERE a.status = 'active' AND a.models LIKE :modelLike")
    List<AccountAbilityJpaEntity> findActiveByModelLike(@Param("modelLike") String modelLike);
}
