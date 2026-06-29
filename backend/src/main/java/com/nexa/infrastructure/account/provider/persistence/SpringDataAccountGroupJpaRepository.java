package com.nexa.infrastructure.account.provider.persistence;

import com.nexa.infrastructure.account.provider.persistence.entity.AccountGroupJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Spring Data JPA 仓库（账号-分组关联，基础设施层内部接口）。
 *
 * <p>仅供 {@link AccountRepositoryImpl} 维护 account_groups fan-out/fan-in。</p>
 */
interface SpringDataAccountGroupJpaRepository
        extends JpaRepository<AccountGroupJpaEntity, AccountGroupJpaEntity.AccountGroupPK> {

    /**
     * 按账号 id 列出关联。
     *
     * @param accountId 账号 id
     * @return 关联列表
     */
    List<AccountGroupJpaEntity> findByAccountId(Long accountId);

    /**
     * 按分组列出关联（选 account 时按 group 反查账号成员）。
     *
     * @param group 字符串分组
     * @return 该分组下的账号关联列表（含组内优先级）
     */
    List<AccountGroupJpaEntity> findByGroup(String group);

    /**
     * 按账号 id 删除全部关联（fan-in，账号 delete/重建前清理）。
     *
     * @param accountId 账号 id
     */
    @Modifying
    @Query("DELETE FROM AccountGroupJpaEntity ag WHERE ag.accountId = :accountId")
    void deleteByAccountId(Long accountId);
}
