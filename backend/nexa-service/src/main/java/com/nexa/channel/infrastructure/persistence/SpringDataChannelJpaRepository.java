package com.nexa.channel.infrastructure.persistence;

import com.nexa.channel.infrastructure.persistence.entity.ChannelJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA 仓库（渠道，基础设施层内部接口）。
 *
 * <p>仅供 {@link ChannelRepositoryImpl} 内部使用，不直接暴露给应用/领域层——领域只认
 * {@code domain.repository.ChannelRepository}。多条件过滤与关键词搜索用 JPQL（可空条件用
 * {@code :param IS NULL OR ...} 惯用法），分页用 {@link Pageable}。</p>
 */
interface SpringDataChannelJpaRepository extends JpaRepository<ChannelJpaEntity, Long> {

    /**
     * 多条件过滤分页查询（group/type/tag/status 均可空，null 表示该维度不过滤，F-2016 列表）。
     *
     * @param group    分组过滤（可空）
     * @param type     type 过滤（可空）
     * @param tag      tag 过滤（可空）
     * @param status   状态过滤（可空）
     * @param pageable 分页
     * @return 当前页实体
     */
    @Query("""
            SELECT c FROM ChannelJpaEntity c
            WHERE (:group IS NULL OR c.group = :group)
              AND (:type IS NULL OR c.type = :type)
              AND (:tag IS NULL OR c.tag = :tag)
              AND (:status IS NULL OR c.status = :status)
            ORDER BY c.id ASC
            """)
    List<ChannelJpaEntity> findPage(@Param("group") String group,
                                    @Param("type") Integer type,
                                    @Param("tag") String tag,
                                    @Param("status") Integer status,
                                    Pageable pageable);

    /**
     * 多条件过滤计数（F-2016 列表 total）。
     *
     * @param group  分组过滤（可空）
     * @param type   type 过滤（可空）
     * @param tag    tag 过滤（可空）
     * @param status 状态过滤（可空）
     * @return 总数
     */
    @Query("""
            SELECT COUNT(c) FROM ChannelJpaEntity c
            WHERE (:group IS NULL OR c.group = :group)
              AND (:type IS NULL OR c.type = :type)
              AND (:tag IS NULL OR c.tag = :tag)
              AND (:status IS NULL OR c.status = :status)
            """)
    long countFiltered(@Param("group") String group,
                       @Param("type") Integer type,
                       @Param("tag") String tag,
                       @Param("status") Integer status);

    /**
     * 关键词搜索分页（名称/模型/分组/标签大小写不敏感包含，F-2016 搜索）。
     *
     * <p>{@code keyword} 已在调用方归一为小写且非空（空白搜索走全量分支，不进本查询）。</p>
     *
     * @param keyword  小写关键词
     * @param pageable 分页
     * @return 当前页实体
     */
    @Query("""
            SELECT c FROM ChannelJpaEntity c
            WHERE LOWER(COALESCE(c.name, '')) LIKE CONCAT('%', :keyword, '%')
               OR LOWER(c.models) LIKE CONCAT('%', :keyword, '%')
               OR LOWER(c.group) LIKE CONCAT('%', :keyword, '%')
               OR LOWER(COALESCE(c.tag, '')) LIKE CONCAT('%', :keyword, '%')
            ORDER BY c.id ASC
            """)
    List<ChannelJpaEntity> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 关键词搜索计数（F-2016 搜索 total）。
     *
     * @param keyword 小写关键词
     * @return 命中数
     */
    @Query("""
            SELECT COUNT(c) FROM ChannelJpaEntity c
            WHERE LOWER(COALESCE(c.name, '')) LIKE CONCAT('%', :keyword, '%')
               OR LOWER(c.models) LIKE CONCAT('%', :keyword, '%')
               OR LOWER(c.group) LIKE CONCAT('%', :keyword, '%')
               OR LOWER(COALESCE(c.tag, '')) LIKE CONCAT('%', :keyword, '%')
            """)
    long countByKeyword(@Param("keyword") String keyword);

    /**
     * 按 tag 列出渠道（F-2019）。
     *
     * @param tag 标签
     * @return 该 tag 下实体列表
     */
    List<ChannelJpaEntity> findByTag(String tag);

    /**
     * 按 id 集合批量加载（F-2016 批量操作）。
     *
     * @param ids id 集合
     * @return 命中实体列表
     */
    List<ChannelJpaEntity> findByIdIn(List<Long> ids);
}
