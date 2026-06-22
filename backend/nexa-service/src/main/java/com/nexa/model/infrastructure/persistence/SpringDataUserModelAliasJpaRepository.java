package com.nexa.model.infrastructure.persistence;

import com.nexa.model.infrastructure.persistence.entity.UserModelAliasJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA 仓库（客户层自助映射，基础设施层内部接口）。
 *
 * <p>仅供 {@link UserModelAliasRepositoryImpl} 内部使用。
 * 越权 self-scope 护栏在应用层落地，仓储层仅按 (scope_type, scope_id) 过滤。</p>
 *
 * <p>注意：JPQL {@code FROM} 用 Hibernate 实体逻辑名 {@code ModelUserModelAliasJpaEntity}
 * （与 relay 包同名类区分）；Java 泛型/返回类型用真实类名 {@link UserModelAliasJpaEntity}。</p>
 */
interface SpringDataUserModelAliasJpaRepository extends JpaRepository<UserModelAliasJpaEntity, Long> {

    /** 按 (scope_type, scope_id, alias) 查（uk_scope_alias 幂等键）。 */
    Optional<UserModelAliasJpaEntity> findByScopeTypeAndScopeIdAndAlias(
            String scopeType, String scopeId, String alias);

    /** 列出某 (scope_type, scope_id) 下全部映射（alias 升序）。 */
    @Query("SELECT a FROM ModelUserModelAliasJpaEntity a WHERE a.scopeType = :scopeType AND a.scopeId = :scopeId ORDER BY a.alias ASC")
    List<UserModelAliasJpaEntity> findByScopeTypeAndScopeId(
            @Param("scopeType") String scopeType, @Param("scopeId") String scopeId);

    /**
     * 列出本人 user-scope + 指定 group-scope 合并（F-6003 列表；优先级 user>group 由调用方处理）。
     *
     * <p>查询 scope_type=user AND scope_id=:userId，或 scope_type=group AND scope_id IN :groups。</p>
     */
    @Query("""
            SELECT a FROM ModelUserModelAliasJpaEntity a
            WHERE (a.scopeType = 'user' AND a.scopeId = :userId)
               OR (a.scopeType = 'group' AND a.scopeId IN :groups)
            ORDER BY a.scopeType ASC, a.alias ASC
            """)
    List<UserModelAliasJpaEntity> findByUserAndGroups(
            @Param("userId") String userId, @Param("groups") List<String> groups);

    /** 软删除（仅对存活行生效）。 */
    @Modifying
    @Query("UPDATE ModelUserModelAliasJpaEntity a SET a.deletedAt = :deletedAt WHERE a.id = :id AND a.deletedAt IS NULL")
    int softDeleteById(@Param("id") long id, @Param("deletedAt") long deletedAt);
}
