package com.nexa.infrastructure.token.persistence;

import com.nexa.infrastructure.token.persistence.entity.TokenJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA 仓库（令牌，基础设施层内部接口）。
 *
 * <p>仅供 {@link TokenRepositoryImpl} 内部使用，不直接暴露给应用/领域层——领域只认
 * {@code domain.repository.TokenRepository}。self-scope 一律通过 SQL 强制 user_id 过滤（ROLE-PERMISSION-MATRIX §3）。
 * 软删除查询由 {@code @SQLRestriction("deleted_at IS NULL")} 自动过滤；写软删用 {@code @Modifying UPDATE}。</p>
 */
interface SpringDataTokenJpaRepository extends JpaRepository<TokenJpaEntity, Long> {

    /**
     * 按明文 key 等值查询（F-3012 用量查询 tokenReadAuth 反查；软删由 @SQLRestriction 过滤）。
     *
     * @param key 完整明文 key
     * @return 命中实体（可空）
     */
    java.util.Optional<TokenJpaEntity> findByKey(String key);

    /**
     * 分页查询某用户的令牌（F-3002 列表，按 id 降序——新建在前）。
     *
     * <p>软删除由实体 {@code @SQLRestriction} 过滤，无需显式 {@code deleted_at IS NULL}。</p>
     *
     * @param userId   归属用户 id
     * @param pageable 分页
     * @return 当前页实体
     */
    @Query("SELECT t FROM TokenJpaEntity t WHERE t.userId = :userId ORDER BY t.id DESC")
    List<TokenJpaEntity> findPageByUser(@Param("userId") int userId, Pageable pageable);

    /**
     * 统计某用户的令牌总数（F-3002 total）。
     *
     * @param userId 归属用户 id
     * @return 总数
     */
    @Query("SELECT COUNT(t) FROM TokenJpaEntity t WHERE t.userId = :userId")
    long countByUser(@Param("userId") int userId);

    /**
     * 关键词搜索某用户的令牌（F-3003，名称模糊，按 id 降序）。
     *
     * <p>{@code keyword} 已在调用方归一为小写且非空（空白搜索走全量分支，不进本查询）。</p>
     *
     * @param userId   归属用户 id
     * @param keyword  小写关键词
     * @param pageable 分页
     * @return 当前页实体
     */
    @Query("""
            SELECT t FROM TokenJpaEntity t
            WHERE t.userId = :userId
              AND LOWER(COALESCE(t.name, '')) LIKE CONCAT('%', :keyword, '%')
            ORDER BY t.id DESC
            """)
    List<TokenJpaEntity> searchByUser(@Param("userId") int userId,
                                      @Param("keyword") String keyword,
                                      Pageable pageable);

    /**
     * 关键词搜索计数（F-3003 total）。
     *
     * @param userId  归属用户 id
     * @param keyword 小写关键词
     * @return 命中数
     */
    @Query("""
            SELECT COUNT(t) FROM TokenJpaEntity t
            WHERE t.userId = :userId
              AND LOWER(COALESCE(t.name, '')) LIKE CONCAT('%', :keyword, '%')
            """)
    long countSearchByUser(@Param("userId") int userId, @Param("keyword") String keyword);

    /**
     * 按 id 集合 + user_id 批量加载（F-3005/F-3007，强制 self-scope）。
     *
     * @param userId 归属用户 id
     * @param ids    id 集合
     * @return 命中且归属该用户的实体列表
     */
    @Query("SELECT t FROM TokenJpaEntity t WHERE t.userId = :userId AND t.id IN :ids")
    List<TokenJpaEntity> findByUserAndIds(@Param("userId") int userId, @Param("ids") List<Long> ids);

    /**
     * 软删除单个令牌（F-3007，写 deleted_at）。
     *
     * <p>直接 UPDATE 绕过 {@code @SQLRestriction}（否则查不到已删行）——用 JPQL 不追加 restriction。
     * 仅当 deleted_at 为 NULL 时更新，幂等防二次刷。</p>
     *
     * @param id        令牌主键
     * @param deletedAt 软删除时间戳（epoch ms 或秒，由调用方统一）
     * @return 受影响行数（0=已删/不存在，1=本次删除）
     */
    @Modifying
    @Query("UPDATE TokenJpaEntity t SET t.deletedAt = :deletedAt WHERE t.id = :id AND t.deletedAt IS NULL")
    int softDeleteById(@Param("id") long id, @Param("deletedAt") long deletedAt);

    /**
     * 批量软删除某用户的令牌（F-3007，强制 self-scope）。
     *
     * @param userId    归属用户 id
     * @param ids       id 集合
     * @param deletedAt 软删除时间戳
     * @return 实际受影响行数
     */
    @Modifying
    @Query("""
            UPDATE TokenJpaEntity t SET t.deletedAt = :deletedAt
            WHERE t.userId = :userId AND t.id IN :ids AND t.deletedAt IS NULL
            """)
    int softDeleteByUserAndIds(@Param("userId") int userId,
                               @Param("ids") List<Long> ids,
                               @Param("deletedAt") long deletedAt);
}
