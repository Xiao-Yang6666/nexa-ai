package com.nexa.infrastructure.model.persistence;

import com.nexa.infrastructure.model.persistence.entity.PublicModelJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA 仓库（对外模型，基础设施层内部接口）。
 *
 * <p>仅供 {@link PublicModelRepositoryImpl} 内部使用。软删除查询由 {@code @SQLRestriction("deleted_at IS NULL")}
 * 自动过滤；写软删用 {@code @Modifying UPDATE}。</p>
 */
interface SpringDataPublicModelJpaRepository extends JpaRepository<PublicModelJpaEntity, Long> {

    /** 按对外名 A 等值查询（uk_public_name 幂等键查重）。 */
    Optional<PublicModelJpaEntity> findByPublicName(String publicName);

    /** 分页列表（sort_order/id 升序）。 */
    @Query("SELECT m FROM PublicModelJpaEntity m ORDER BY m.sortOrder ASC, m.id ASC")
    List<PublicModelJpaEntity> findPageOrdered(Pageable pageable);

    /** 仅 enabled=true 的分页列表。 */
    @Query("SELECT m FROM PublicModelJpaEntity m WHERE m.enabled = true ORDER BY m.sortOrder ASC, m.id ASC")
    List<PublicModelJpaEntity> findEnabledPageOrdered(Pageable pageable);

    /** 全部总数。 */
    long count();

    /** 仅 enabled=true 的总数。 */
    @Query("SELECT COUNT(m) FROM PublicModelJpaEntity m WHERE m.enabled = true")
    long countEnabled();

    /**
     * 上架公开名 A 列表（F-6003 候选层来源，F-6004 全员可用判定；不含任何 B，UserVO 安全）。
     */
    @Query("SELECT m.publicName FROM PublicModelJpaEntity m WHERE m.enabled = true ORDER BY m.sortOrder ASC, m.id ASC")
    List<String> findEnabledPublicNames();

    /**
     * 全部上架对外模型完整行（F-2048 公开价格页定价主体；不分页，按 sort_order/id 升序）。
     */
    @Query("SELECT m FROM PublicModelJpaEntity m WHERE m.enabled = true ORDER BY m.sortOrder ASC, m.id ASC")
    List<PublicModelJpaEntity> findAllEnabledOrdered();

    /** 软删除（仅对存活行生效）。 */
    @Modifying
    @Query("UPDATE PublicModelJpaEntity m SET m.deletedAt = :deletedAt WHERE m.id = :id AND m.deletedAt IS NULL")
    int softDeleteById(@Param("id") long id, @Param("deletedAt") long deletedAt);
}
