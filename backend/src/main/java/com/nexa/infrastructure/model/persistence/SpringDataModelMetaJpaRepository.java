package com.nexa.infrastructure.model.persistence;

import com.nexa.infrastructure.model.persistence.entity.ModelMetaJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA 仓库（模型元数据，基础设施层内部接口）。
 *
 * <p>仅供 {@link ModelMetaRepositoryImpl} 内部使用，不直接暴露给应用/领域层——领域只认
 * {@code domain.repository.ModelMetaRepository}。多条件搜索用 JPQL（可空条件用
 * {@code :param IS NULL OR ...} 惯用法）；软删除查询由 {@code @SQLRestriction} 自动过滤，写软删用
 * {@code @Modifying UPDATE}。供应商计数用聚合查询一次取回（F-3013 enrich 避免 N+1）。</p>
 */
interface SpringDataModelMetaJpaRepository extends JpaRepository<ModelMetaJpaEntity, Long> {

    /**
     * 按模型名等值查询（F-3015/F-3016/F-3019 幂等键查重）。
     *
     * @param modelName 模型名
     * @return 命中实体
     */
    Optional<ModelMetaJpaEntity> findByModelName(String modelName);

    /**
     * 分页列表（按 id 升序，F-3013）。
     *
     * @param pageable 分页
     * @return 当前页实体
     */
    @Query("SELECT m FROM ModelMetaJpaEntity m ORDER BY m.id ASC")
    List<ModelMetaJpaEntity> findPageOrdered(Pageable pageable);

    /**
     * 关键词 + 供应商过滤分页搜索（F-3014）。
     *
     * <p>keyword 已归一为小写（空白等价不过滤，传空串 → LIKE %% 命中全部）；vendorId 可空（null 不过滤）。</p>
     *
     * @param keyword  小写关键词（可空串）
     * @param vendorId 供应商过滤（可空）
     * @param pageable 分页
     * @return 当前页实体
     */
    @Query("""
            SELECT m FROM ModelMetaJpaEntity m
            WHERE (:vendorId IS NULL OR m.vendorId = :vendorId)
              AND (LOWER(m.modelName) LIKE CONCAT('%', :keyword, '%')
                   OR LOWER(COALESCE(m.tags, '')) LIKE CONCAT('%', :keyword, '%')
                   OR LOWER(COALESCE(m.description, '')) LIKE CONCAT('%', :keyword, '%'))
            ORDER BY m.id ASC
            """)
    List<ModelMetaJpaEntity> searchFiltered(@Param("keyword") String keyword,
                                            @Param("vendorId") Long vendorId,
                                            Pageable pageable);

    /**
     * 关键词 + 供应商过滤搜索计数（F-3014 total）。
     *
     * @param keyword  小写关键词（可空串）
     * @param vendorId 供应商过滤（可空）
     * @return 命中数
     */
    @Query("""
            SELECT COUNT(m) FROM ModelMetaJpaEntity m
            WHERE (:vendorId IS NULL OR m.vendorId = :vendorId)
              AND (LOWER(m.modelName) LIKE CONCAT('%', :keyword, '%')
                   OR LOWER(COALESCE(m.tags, '')) LIKE CONCAT('%', :keyword, '%')
                   OR LOWER(COALESCE(m.description, '')) LIKE CONCAT('%', :keyword, '%'))
            """)
    long countFiltered(@Param("keyword") String keyword, @Param("vendorId") Long vendorId);

    /**
     * 按供应商分组计数（F-3013 vendor_counts，一次聚合避免 N+1）。
     *
     * @return [vendorId, count] 行（vendorId 可能为 null）
     */
    @Query("SELECT m.vendorId, COUNT(m) FROM ModelMetaJpaEntity m GROUP BY m.vendorId")
    List<Object[]> countGroupByVendor();

    /**
     * 软删除模型（F-3017，写 deleted_at；仅 deleted_at 为 NULL 时更新，幂等）。
     *
     * @param id        主键
     * @param deletedAt 软删除时间戳 epoch 秒
     * @return 受影响行数
     */
    @Modifying
    @Query("UPDATE ModelMetaJpaEntity m SET m.deletedAt = :deletedAt WHERE m.id = :id AND m.deletedAt IS NULL")
    int softDeleteById(@Param("id") long id, @Param("deletedAt") long deletedAt);
}
