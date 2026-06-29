package com.nexa.infrastructure.model.persistence;

import com.nexa.infrastructure.model.persistence.po.VendorMetaPO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA 仓库（供应商元数据，基础设施层内部接口）。
 *
 * <p>仅供 {@link VendorRepositoryImpl} 内部使用，不直接暴露给应用/领域层——领域只认
 * {@code domain.repository.VendorRepository}。软删除查询由 {@code @SQLRestriction("deleted_at IS NULL")}
 * 自动过滤；写软删用 {@code @Modifying UPDATE}（绕过 restriction 才能命中已删行幂等）。</p>
 */
interface SpringDataVendorMetaJpaRepository extends JpaRepository<VendorMetaPO, Long> {

    /**
     * 按名称等值查询（F-3018 幂等键查重；软删由 @SQLRestriction 过滤）。
     *
     * @param name 供应商名
     * @return 命中实体
     */
    Optional<VendorMetaPO> findByName(String name);

    /**
     * 分页列表（按 id 升序，F-3018）。
     *
     * @param pageable 分页
     * @return 当前页实体
     */
    @Query("SELECT v FROM VendorMetaPO v ORDER BY v.id ASC")
    List<VendorMetaPO> findPageOrdered(Pageable pageable);

    /**
     * 关键词搜索（按名称大小写不敏感包含，F-3018，全量不分页）。
     *
     * @param keyword 小写关键词
     * @return 命中实体列表
     */
    @Query("""
            SELECT v FROM VendorMetaPO v
            WHERE LOWER(v.name) LIKE CONCAT('%', :keyword, '%')
            ORDER BY v.id ASC
            """)
    List<VendorMetaPO> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 软删除供应商（F-3018，写 deleted_at；仅 deleted_at 为 NULL 时更新，幂等）。
     *
     * @param id        主键
     * @param deletedAt 软删除时间戳 epoch 秒
     * @return 受影响行数
     */
    @Modifying
    @Query("UPDATE VendorMetaPO v SET v.deletedAt = :deletedAt WHERE v.id = :id AND v.deletedAt IS NULL")
    int softDeleteById(@Param("id") long id, @Param("deletedAt") long deletedAt);
}
