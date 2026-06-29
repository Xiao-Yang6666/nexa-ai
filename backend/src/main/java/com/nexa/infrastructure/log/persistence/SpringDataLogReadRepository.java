package com.nexa.infrastructure.log.persistence;

import com.nexa.infrastructure.log.persistence.entity.LogReadJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA 仓库（日志读侧，基础设施层内部接口）。
 *
 * <p>仅供 {@link LogRepositoryImpl} 内部使用，领域层只认 {@code domain.repository.LogRepository}。
 * 多条件过滤沿用全站 {@code :param IS NULL OR ...} 可空参数惯用法（与 channel/token 同构）；
 * 按日聚合 / 排行用原生 SQL（PG 日期分组 {@code to_char(to_timestamp(...))} 与窗口聚合，JPQL 难表达）；
 * 历史清理用原生分批 DELETE（PG 不支持 {@code DELETE ... LIMIT}，以子查询 + ctid 限批）。</p>
 *
 * <p>self-scope：{@code userId} 维度由可空参数控制，自助查询永远传非空 userId（用例层从认证上下文取，
 * 防伪造），管理端传 null=全站（ROLE-PERMISSION-MATRIX §3）。</p>
 */
interface SpringDataLogReadRepository extends JpaRepository<LogReadJpaEntity, Long> {

    /**
     * 多条件过滤分页查询日志（F-4001 管理端 / F-4002 自助；按 created_at,id 降序新→旧）。
     *
     * <p>所有过滤维度可空（null=该维度不过滤）。{@code userId} 非空即强制 self-scope；
     * 模型按 model_name(=C) 口径过滤（与现网报表一致）。</p>
     */
    @Query("""
            SELECT l FROM LogReadJpaEntity l
            WHERE (:userId IS NULL OR l.userId = :userId)
              AND (:type IS NULL OR l.type = :type)
              AND (:startTs IS NULL OR l.createdAt >= :startTs)
              AND (:endTs IS NULL OR l.createdAt <= :endTs)
              AND (:username IS NULL OR l.username = :username)
              AND (:tokenName IS NULL OR l.tokenName = :tokenName)
              AND (:modelName IS NULL OR l.modelName = :modelName)
              AND (:channelId IS NULL OR l.channelId = :channelId)
              AND (:grp IS NULL OR l.group = :grp)
              AND (:requestId IS NULL OR l.requestId = :requestId)
              AND (:upstreamRequestId IS NULL OR l.upstreamRequestId = :upstreamRequestId)
            ORDER BY l.createdAt DESC, l.id DESC
            """)
    List<LogReadJpaEntity> findByFilter(@Param("userId") Integer userId,
                                        @Param("type") Integer type,
                                        @Param("startTs") Long startTs,
                                        @Param("endTs") Long endTs,
                                        @Param("username") String username,
                                        @Param("tokenName") String tokenName,
                                        @Param("modelName") String modelName,
                                        @Param("channelId") Integer channelId,
                                        @Param("grp") String grp,
                                        @Param("requestId") String requestId,
                                        @Param("upstreamRequestId") String upstreamRequestId,
                                        Pageable pageable);

    /**
     * 多条件过滤计数（F-4001/F-4002 分页 total，维度与 {@link #findByFilter} 一致）。
     */
    @Query("""
            SELECT COUNT(l) FROM LogReadJpaEntity l
            WHERE (:userId IS NULL OR l.userId = :userId)
              AND (:type IS NULL OR l.type = :type)
              AND (:startTs IS NULL OR l.createdAt >= :startTs)
              AND (:endTs IS NULL OR l.createdAt <= :endTs)
              AND (:username IS NULL OR l.username = :username)
              AND (:tokenName IS NULL OR l.tokenName = :tokenName)
              AND (:modelName IS NULL OR l.modelName = :modelName)
              AND (:channelId IS NULL OR l.channelId = :channelId)
              AND (:grp IS NULL OR l.group = :grp)
              AND (:requestId IS NULL OR l.requestId = :requestId)
              AND (:upstreamRequestId IS NULL OR l.upstreamRequestId = :upstreamRequestId)
            """)
    long countByFilter(@Param("userId") Integer userId,
                       @Param("type") Integer type,
                       @Param("startTs") Long startTs,
                       @Param("endTs") Long endTs,
                       @Param("username") String username,
                       @Param("tokenName") String tokenName,
                       @Param("modelName") String modelName,
                       @Param("channelId") Integer channelId,
                       @Param("grp") String grp,
                       @Param("requestId") String requestId,
                       @Param("upstreamRequestId") String upstreamRequestId);

    /**
     * 按令牌 id 查消费日志（F-4003，仅 Type=2，按 created_at,id 降序）。
     *
     * @param tokenId  令牌 id
     * @param pageable 上限（PageRequest.of(0, limit)）
     * @return 该令牌消费日志（新→旧）
     */
    @Query("""
            SELECT l FROM LogReadJpaEntity l
            WHERE l.tokenId = :tokenId AND l.type = 2
            ORDER BY l.createdAt DESC, l.id DESC
            """)
    List<LogReadJpaEntity> findConsumeByToken(@Param("tokenId") Integer tokenId, Pageable pageable);

    /**
     * 聚合消费日志的 [请求数, quota 总和, token 总和]（F-4004/F-4005；强制 Type=2）。
     *
     * <p>返回单行三列：{@code COUNT(*), SUM(quota), SUM(prompt+completion)}。过滤维度可空。
     * 用 JPQL 聚合（Object[] 投影），由 impl 转 long[]。</p>
     */
    @Query("""
            SELECT COUNT(l),
                   COALESCE(SUM(l.quota), 0),
                   COALESCE(SUM(l.promptTokens + l.completionTokens), 0)
            FROM LogReadJpaEntity l
            WHERE l.type = 2
              AND (:userId IS NULL OR l.userId = :userId)
              AND (:startTs IS NULL OR l.createdAt >= :startTs)
              AND (:endTs IS NULL OR l.createdAt <= :endTs)
              AND (:username IS NULL OR l.username = :username)
              AND (:tokenName IS NULL OR l.tokenName = :tokenName)
              AND (:modelName IS NULL OR l.modelName = :modelName)
              AND (:grp IS NULL OR l.group = :grp)
            """)
    Object[] aggregateConsume(@Param("userId") Integer userId,
                              @Param("startTs") Long startTs,
                              @Param("endTs") Long endTs,
                              @Param("username") String username,
                              @Param("tokenName") String tokenName,
                              @Param("modelName") String modelName,
                              @Param("grp") String grp);

    /**
     * 按日 + 模型聚合配额数据（F-4007/F-4008/F-4009；原生 PG SQL，仅消费 type=2）。
     *
     * <p>PG 把 epoch 秒 {@code created_at} 转日期再 group。{@code userId}/{@code username} 可空
     * （null 用 {@code :p IS NULL OR ...} 模拟）。返回列序：date(text), quota(bigint), count(bigint), model_name。</p>
     */
    @Query(value = """
            SELECT to_char(to_timestamp(l.created_at), 'YYYY-MM-DD') AS day,
                   COALESCE(SUM(l.quota), 0) AS quota,
                   COUNT(*) AS cnt,
                   l.model_name AS model_name
            FROM logs l
            WHERE l.type = 2
              AND (CAST(:userId AS INTEGER) IS NULL OR l.user_id = :userId)
              AND (CAST(:username AS VARCHAR) IS NULL OR l.username = :username)
              AND (CAST(:startTs AS BIGINT) IS NULL OR l.created_at >= :startTs)
              AND (CAST(:endTs AS BIGINT) IS NULL OR l.created_at <= :endTs)
            GROUP BY day, l.model_name
            ORDER BY day ASC
            """, nativeQuery = true)
    List<Object[]> aggregateQuotaByDay(@Param("userId") Integer userId,
                                       @Param("username") String username,
                                       @Param("startTs") Long startTs,
                                       @Param("endTs") Long endTs);

    /**
     * 实时聚合用量排行（F-4010；原生 PG SQL，按对外公开名 A 分组、消费量降序）。
     *
     * <p>窗口下界 {@code created_at >= :since}（since = now - period.lookback）。只取 type=2 消费，
     * 按 {@code resolved_public_model}(A) 分组 SUM(quota) 降序——绝不按 B/渠道（可见性铁律）。
     * 返回列序：public_model(text), used_quota(bigint)。</p>
     */
    @Query(value = """
            SELECT l.resolved_public_model AS public_model,
                   COALESCE(SUM(l.quota), 0) AS used_quota
            FROM logs l
            WHERE l.type = 2
              AND l.created_at >= :since
              AND l.resolved_public_model <> ''
            GROUP BY l.resolved_public_model
            ORDER BY used_quota DESC
            LIMIT :lim
            """, nativeQuery = true)
    List<Object[]> aggregateRanking(@Param("since") long since, @Param("lim") int lim);

    /**
     * 按对外公开名 A 聚合利润看板（F-6009 dimension=model；原生 PG，仅 type=2 消费）。
     *
     * <p>按 {@code resolved_public_model}(A) 分组，SUM 售价/成本/利润，并统计成本缺失条数
     * （{@code quota_cost=0 AND quota_sell>0}）。绝不暴露上游模型 B/渠道明细（可见性铁律）。
     * 时间区间可空（{@code CAST(:p AS BIGINT) IS NULL OR ...}）。空键剔除。按利润降序。
     * 返回列序：dimension_key(text), sum_sell, sum_cost, sum_profit, cost_missing_count, request_count（均 bigint）。</p>
     */
    @Query(value = """
            SELECT l.resolved_public_model AS dimension_key,
                   COALESCE(SUM(l.quota_sell), 0)   AS sum_sell,
                   COALESCE(SUM(l.quota_cost), 0)   AS sum_cost,
                   COALESCE(SUM(l.quota_profit), 0) AS sum_profit,
                   COUNT(*) FILTER (WHERE l.quota_cost = 0 AND l.quota_sell > 0) AS cost_missing,
                   COUNT(*) AS request_count
            FROM logs l
            WHERE l.type = 2
              AND l.resolved_public_model <> ''
              AND (CAST(:startTs AS BIGINT) IS NULL OR l.created_at >= :startTs)
              AND (CAST(:endTs AS BIGINT) IS NULL OR l.created_at <= :endTs)
            GROUP BY l.resolved_public_model
            ORDER BY sum_profit DESC
            """, nativeQuery = true)
    List<Object[]> aggregateProfitByModel(@Param("startTs") Long startTs, @Param("endTs") Long endTs);

    /**
     * 按渠道名聚合利润看板（F-6009 dimension=channel；原生 PG，仅 type=2 消费）。
     *
     * <p>按 {@code channel_name} 分组（NULL/空合并为 'unknown' 占位），口径同
     * {@link #aggregateProfitByModel}。</p>
     */
    @Query(value = """
            SELECT COALESCE(NULLIF(l.channel_name, ''), 'unknown') AS dimension_key,
                   COALESCE(SUM(l.quota_sell), 0)   AS sum_sell,
                   COALESCE(SUM(l.quota_cost), 0)   AS sum_cost,
                   COALESCE(SUM(l.quota_profit), 0) AS sum_profit,
                   COUNT(*) FILTER (WHERE l.quota_cost = 0 AND l.quota_sell > 0) AS cost_missing,
                   COUNT(*) AS request_count
            FROM logs l
            WHERE l.type = 2
              AND (CAST(:startTs AS BIGINT) IS NULL OR l.created_at >= :startTs)
              AND (CAST(:endTs AS BIGINT) IS NULL OR l.created_at <= :endTs)
            GROUP BY COALESCE(NULLIF(l.channel_name, ''), 'unknown')
            ORDER BY sum_profit DESC
            """, nativeQuery = true)
    List<Object[]> aggregateProfitByChannel(@Param("startTs") Long startTs, @Param("endTs") Long endTs);

    /**
     * 按用户分组聚合利润看板（F-6009 dimension=group；原生 PG，仅 type=2 消费）。
     *
     * <p>按 {@code "group"} 分组（NULL/空合并为 'default' 占位），口径同
     * {@link #aggregateProfitByModel}。{@code group} 是 PG 保留字，须加双引号。</p>
     */
    @Query(value = """
            SELECT COALESCE(NULLIF(l."group", ''), 'default') AS dimension_key,
                   COALESCE(SUM(l.quota_sell), 0)   AS sum_sell,
                   COALESCE(SUM(l.quota_cost), 0)   AS sum_cost,
                   COALESCE(SUM(l.quota_profit), 0) AS sum_profit,
                   COUNT(*) FILTER (WHERE l.quota_cost = 0 AND l.quota_sell > 0) AS cost_missing,
                   COUNT(*) AS request_count
            FROM logs l
            WHERE l.type = 2
              AND (CAST(:startTs AS BIGINT) IS NULL OR l.created_at >= :startTs)
              AND (CAST(:endTs AS BIGINT) IS NULL OR l.created_at <= :endTs)
            GROUP BY COALESCE(NULLIF(l."group", ''), 'default')
            ORDER BY sum_profit DESC
            """, nativeQuery = true)
    List<Object[]> aggregateProfitByGroup(@Param("startTs") Long startTs, @Param("endTs") Long endTs);

    /**
     * 分批清理早于目标时间的日志（F-4006，每批上限 batchSize；原生 PG，ctid 限批 DELETE）。
     *
     * <p>PG 不支持 {@code DELETE ... LIMIT}，故以子查询选出 ≤ batchSize 行的 ctid 再删。
     * 调用方循环调用直到返回 0（分批，避免一次锁全表）。</p>
     *
     * @param targetTimestamp 删 created_at &lt; 该值
     * @param batchSize       单批上限
     * @return 本批实际删除行数
     */
    @Modifying
    @Query(value = """
            DELETE FROM logs
            WHERE ctid IN (
                SELECT ctid FROM logs
                WHERE created_at < :targetTimestamp
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int purgeBatch(@Param("targetTimestamp") long targetTimestamp, @Param("batchSize") int batchSize);

    /**
     * 匿名化某用户历史日志的 username 归属字段（F-5020 账号注销级联）。
     *
     * <p>把该 user_id 名下所有日志的 username 置为匿名占位，不删本体（保留计量/审计聚合价值）。
     * {@code @Modifying} 批量 UPDATE，返回受影响行数。logs 表 user_id 为 int 列，入参窄化为 Integer。</p>
     *
     * @param userId             被注销用户 id（int 列）
     * @param anonymizedUsername 匿名占位用户名
     * @return 受影响（匿名化）行数
     */
    @Modifying
    @Query("UPDATE LogReadJpaEntity l SET l.username = :anon WHERE l.userId = :userId")
    int anonymizeByUserId(@Param("userId") Integer userId, @Param("anon") String anonymizedUsername);
}
