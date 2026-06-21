package com.nexa.model.infrastructure.persistence;

import com.nexa.model.infrastructure.persistence.entity.PlatformModelMappingJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA 仓库（底仓映射，基础设施层内部接口）。
 *
 * <p>仅供 {@link PlatformModelMappingRepositoryImpl} 内部使用。
 * <b>B 不可见数据层闸</b>：本接口无客户可触达路径，所有方法均在 admin/root 应用层调用。</p>
 *
 * <p>注意：JPQL {@code FROM} 用 Hibernate 实体逻辑名 {@code ModelPlatformModelMappingJpaEntity}
 * （与 relay 包同名类区分）；Java 泛型/返回类型用真实类名 {@link PlatformModelMappingJpaEntity}。</p>
 */
interface SpringDataPlatformModelMappingJpaRepository extends JpaRepository<PlatformModelMappingJpaEntity, Long> {

    /** 按对外名 A 查（uk_pmm_public_name 幂等键）。 */
    Optional<PlatformModelMappingJpaEntity> findByPublicName(String publicName);

    /** 分页列表（id 升序）。 */
    @Query("SELECT m FROM ModelPlatformModelMappingJpaEntity m ORDER BY m.id ASC")
    List<PlatformModelMappingJpaEntity> findPageOrdered(Pageable pageable);

    /** 软删除（仅对存活行生效）。 */
    @Modifying
    @Query("UPDATE ModelPlatformModelMappingJpaEntity m SET m.deletedAt = :deletedAt WHERE m.id = :id AND m.deletedAt IS NULL")
    int softDeleteById(@Param("id") long id, @Param("deletedAt") long deletedAt);
}
