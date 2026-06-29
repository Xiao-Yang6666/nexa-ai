package com.nexa.infrastructure.relay.persistence;

import com.nexa.infrastructure.relay.persistence.entity.UserModelAliasJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 客户层别名 Spring Data JPA 仓库（基础设施层细节，F-6011）。
 *
 * <p>由 {@link UserModelAliasRepositoryImpl} 适配为 domain 的
 * {@link com.nexa.domain.relay.repository.UserModelAliasRepository}。</p>
 *
 * <p>注意：JPQL {@code FROM} 用 Hibernate 实体逻辑名 {@code RelayUserModelAliasJpaEntity}
 * （与 model 包同名类区分）；Java 泛型/返回类型用真实类名 {@link UserModelAliasJpaEntity}。</p>
 */
@Repository
public interface SpringDataUserModelAliasRepository extends JpaRepository<UserModelAliasJpaEntity, Long> {

    @Query("SELECT a FROM RelayUserModelAliasJpaEntity a WHERE a.scopeType = :scopeType AND a.scopeId = :scopeId "
            + "AND a.alias = :alias AND a.enabled = true AND a.deletedAt IS NULL")
    Optional<UserModelAliasJpaEntity> findEnabledByScopeAndAlias(@Param("scopeType") String scopeType,
                                                                @Param("scopeId") String scopeId,
                                                                @Param("alias") String alias);

    @Query("SELECT a FROM RelayUserModelAliasJpaEntity a WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<UserModelAliasJpaEntity> findActiveById(@Param("id") Long id);

    @Query("SELECT a FROM RelayUserModelAliasJpaEntity a WHERE a.scopeType = :scopeType AND a.scopeId = :scopeId "
            + "AND a.deletedAt IS NULL ORDER BY a.alias ASC")
    List<UserModelAliasJpaEntity> findByScope(@Param("scopeType") String scopeType, @Param("scopeId") String scopeId);
}
