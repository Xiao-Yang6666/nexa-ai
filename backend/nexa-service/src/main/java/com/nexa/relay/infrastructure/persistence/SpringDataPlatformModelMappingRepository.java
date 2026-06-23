package com.nexa.relay.infrastructure.persistence;

import com.nexa.relay.infrastructure.persistence.entity.PlatformModelMappingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 超管底仓映射 Spring Data JPA 仓库（基础设施层细节，F-6011）。
 *
 * <p>由 {@link PlatformModelMappingRepositoryImpl} 适配为 domain 的
 * {@link com.nexa.relay.domain.repository.PlatformModelMappingRepository}。软删除以 deleted_at IS NULL 过滤。</p>
 *
 * <p>注意：JPQL {@code FROM} 子句使用 Hibernate 实体逻辑名 {@code RelayPlatformModelMappingJpaEntity}
 * （见 {@link PlatformModelMappingJpaEntity} 上的 {@code @Entity(name=...)}，与 model 包同名类区分）；
 * 而 Java 泛型/返回类型用真实类名 {@link PlatformModelMappingJpaEntity}。</p>
 */
@Repository
public interface SpringDataPlatformModelMappingRepository extends JpaRepository<PlatformModelMappingJpaEntity, Long> {

    @Query("SELECT m FROM RelayPlatformModelMappingJpaEntity m WHERE m.publicName = :name "
            + "AND m.enabled = true AND m.deletedAt IS NULL")
    Optional<PlatformModelMappingJpaEntity> findEnabledByPublicName(@Param("name") String name);

    @Query("SELECT m FROM RelayPlatformModelMappingJpaEntity m WHERE m.id = :id AND m.deletedAt IS NULL")
    Optional<PlatformModelMappingJpaEntity> findActiveById(@Param("id") Long id);

    @Query("SELECT m FROM RelayPlatformModelMappingJpaEntity m WHERE m.deletedAt IS NULL ORDER BY m.publicName ASC")
    List<PlatformModelMappingJpaEntity> findAllActive();
}
