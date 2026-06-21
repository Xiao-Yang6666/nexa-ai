package com.nexa.account.infrastructure.persistence;

import com.nexa.account.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data JPA 仓库（基础设施层内部接口）。
 *
 * <p>仅供 {@link UserRepositoryImpl} 内部使用，不直接暴露给应用/领域层——领域只认
 * {@code domain.repository.UserRepository}。命名 {@code SpringDataUserJpaRepository} 以区分领域仓储。</p>
 */
interface SpringDataUserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    /**
     * 按用户名查实体。
     *
     * @param username 用户名
     * @return 命中实体，否则空
     */
    Optional<UserJpaEntity> findByUsername(String username);

    /**
     * 用户名是否存在。
     *
     * @param username 用户名
     * @return 存在返回 true
     */
    boolean existsByUsername(String username);

    /**
     * 按邮箱查实体（找回密码定位账号）。
     *
     * <p>邮箱列非唯一，Spring Data 派生查询返回首个命中。</p>
     *
     * @param email 邮箱
     * @return 命中实体，否则空
     */
    Optional<UserJpaEntity> findByEmail(String email);

    /**
     * 全量分页查询（管理端列表，F-1008，无关键词）。
     *
     * <p>{@code @SQLRestriction("deleted_at IS NULL")} 在实体上自动过滤软删除行，故无需在此显式排除。</p>
     *
     * @param pageable 分页参数（页码/页大小/排序）
     * @return 当页实体
     */
    Page<UserJpaEntity> findAllBy(Pageable pageable);

    /**
     * 关键词分页搜索（管理端搜索，F-1008）。
     *
     * <p>按 username / email / group 大小写不敏感模糊匹配（{@code group} 为 PG 保留字，
     * 在 JPQL 中以实体属性名 {@code u.group} 引用，由 Hibernate 转义为 {@code "group"} 列）。
     * 关键词在调用方已用 {@code %kw%} 包裹后传入。</p>
     *
     * @param keyword  已含通配符的搜索词（如 {@code %alice%}）
     * @param pageable 分页参数
     * @return 命中实体当页
     */
    @Query("""
            SELECT u FROM UserJpaEntity u
            WHERE LOWER(u.username) LIKE LOWER(:keyword)
               OR LOWER(u.email) LIKE LOWER(:keyword)
               OR LOWER(u.group) LIKE LOWER(:keyword)
            """)
    Page<UserJpaEntity> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 软删除：写 {@code deleted_at} 时间戳（管理端删除，F-1010 delete）。
     *
     * <p>用 JPQL UPDATE 直接打时间戳，绕过 {@code @SQLRestriction} 对读的过滤；
     * 删除后该行不再被任何查询返回（DB-SCHEMA §1 软删除）。</p>
     *
     * @param id        目标用户 id
     * @param deletedAt 删除时间戳 epoch 秒
     * @return 受影响行数
     */
    @Modifying
    @Query("UPDATE UserJpaEntity u SET u.deletedAt = :deletedAt WHERE u.id = :id")
    int markDeleted(@Param("id") long id, @Param("deletedAt") long deletedAt);
}
