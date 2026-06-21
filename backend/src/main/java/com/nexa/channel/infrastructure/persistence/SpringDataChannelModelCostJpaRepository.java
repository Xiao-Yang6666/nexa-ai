package com.nexa.channel.infrastructure.persistence;

import com.nexa.channel.infrastructure.persistence.entity.ChannelModelCostJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA 仓库（供应商成本配置，基础设施层内部接口）。
 *
 * <p>仅供 {@link ChannelModelCostRepositoryImpl} 内部使用。客户无任何读路径——B 不可见数据层闸。</p>
 */
interface SpringDataChannelModelCostJpaRepository extends JpaRepository<ChannelModelCostJpaEntity, Long> {

    /** 按 (channel_id, upstream_model) 查（uk_channel_model 幂等键）。 */
    Optional<ChannelModelCostJpaEntity> findByChannelIdAndUpstreamModel(Integer channelId, String upstreamModel);

    /** 分页列表（channel_id 升序、id 升序，无过滤）。 */
    @Query("SELECT c FROM ChannelModelCostJpaEntity c ORDER BY c.channelId ASC, c.id ASC")
    List<ChannelModelCostJpaEntity> findPageOrdered(Pageable pageable);

    /** 按 channel_id 过滤分页列表。 */
    @Query("SELECT c FROM ChannelModelCostJpaEntity c WHERE c.channelId = :channelId ORDER BY c.id ASC")
    List<ChannelModelCostJpaEntity> findByChannelIdPageOrdered(@Param("channelId") int channelId, Pageable pageable);

    /** 按 upstream_model 过滤分页列表（等值匹配）。 */
    @Query("SELECT c FROM ChannelModelCostJpaEntity c WHERE c.upstreamModel = :upstreamModel ORDER BY c.channelId ASC, c.id ASC")
    List<ChannelModelCostJpaEntity> findByUpstreamModelPageOrdered(@Param("upstreamModel") String upstreamModel, Pageable pageable);

    /** 按 channel_id + upstream_model 双条件分页列表。 */
    @Query("SELECT c FROM ChannelModelCostJpaEntity c WHERE c.channelId = :channelId AND c.upstreamModel = :upstreamModel ORDER BY c.id ASC")
    List<ChannelModelCostJpaEntity> findByBothPageOrdered(@Param("channelId") int channelId, @Param("upstreamModel") String upstreamModel, Pageable pageable);

    /** 无过滤总数。 */
    long count();

    /** 按 channel_id 过滤总数。 */
    long countByChannelId(int channelId);

    /** 按 upstream_model 过滤总数。 */
    long countByUpstreamModel(String upstreamModel);

    /** 按双条件过滤总数。 */
    long countByChannelIdAndUpstreamModel(int channelId, String upstreamModel);

    /** 按 channel_id 集合批量加载（供应渠道池 enrich，避免 N+1）。 */
    @Query("SELECT c FROM ChannelModelCostJpaEntity c WHERE c.channelId IN :channelIds")
    List<ChannelModelCostJpaEntity> findByChannelIds(@Param("channelIds") List<Integer> channelIds);

    /** 软删除（仅对存活行生效）。 */
    @Modifying
    @Query("UPDATE ChannelModelCostJpaEntity c SET c.deletedAt = :deletedAt WHERE c.id = :id AND c.deletedAt IS NULL")
    int softDeleteById(@Param("id") long id, @Param("deletedAt") long deletedAt);
}
