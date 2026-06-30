package com.nexa.infrastructure.log.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.log.persistence.po.LogReadPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 日志读侧 MyBatis-Plus Mapper（基础设施层内部接口，取代原 {@code SpringDataLogReadRepository}）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（insert/selectById/selectList/selectPage/selectCount...）。
 * 简单等值多条件过滤（findByFilter/countByFilter/findConsumeByToken）由 {@link LogRepositoryImpl} 内
 * {@code LambdaQueryWrapper} 组装，不在此声明；以下仅声明 JPQL 难表达的 PG 原生聚合 / 分批清理 /
 * 批量匿名化（按对外公开名 A 分组等可见性铁律口径不变，列名为受控常量）。</p>
 *
 * <p><b>可空参数惯用法</b>：沿用原生 {@code CAST(#{p} AS <type>) IS NULL OR col = #{p}}——显式
 * CAST 让 PG 能确定 null 参数的数据类型（否则 “could not determine data type of parameter”），
 * 与迁移前 {@code SpringDataLogReadRepository} 的 native 查询 1:1。</p>
 *
 * <p><b>聚合返回 Map</b>：单/多列聚合以 {@code Map<String,Object>} 承载，列别名一律取无下划线小写
 * （{@code cnt/quota/tokens/...}），规避 {@code mapUnderscoreToCamelCase} 对 Map 键的潜在影响，
 * 由 Impl 按别名读取并归一为领域值对象。</p>
 */
public interface LogReadMapper extends BaseMapper<LogReadPO> {

    /**
     * 聚合消费日志 [请求数, quota 总和, token 总和]（F-4004/F-4005；强制 type=2）。
     *
     * <p>取代原 JPQL {@code aggregateConsume}。返回单行：{@code cnt / quota / tokens}。过滤维度可空
     * （{@code CAST(...) IS NULL OR ...}）。</p>
     *
     * @return 单行聚合 Map（键：cnt、quota、tokens）
     */
    @Select("""
            SELECT COUNT(*) AS cnt,
                   COALESCE(SUM(quota), 0) AS quota,
                   COALESCE(SUM(prompt_tokens + completion_tokens), 0) AS tokens
            FROM logs
            WHERE type = 2
              AND (CAST(#{userId} AS INTEGER) IS NULL OR user_id = #{userId})
              AND (CAST(#{startTs} AS BIGINT) IS NULL OR created_at >= #{startTs})
              AND (CAST(#{endTs} AS BIGINT) IS NULL OR created_at <= #{endTs})
              AND (CAST(#{username} AS VARCHAR) IS NULL OR username = #{username})
              AND (CAST(#{tokenName} AS VARCHAR) IS NULL OR token_name = #{tokenName})
              AND (CAST(#{modelName} AS VARCHAR) IS NULL OR model_name = #{modelName})
              AND (CAST(#{grp} AS VARCHAR) IS NULL OR "group" = #{grp})
            """)
    Map<String, Object> aggregateConsume(@Param("userId") Integer userId,
                                         @Param("startTs") Long startTs,
                                         @Param("endTs") Long endTs,
                                         @Param("username") String username,
                                         @Param("tokenName") String tokenName,
                                         @Param("modelName") String modelName,
                                         @Param("grp") String grp);

    /**
     * 按日 + 模型聚合配额（F-4007/F-4008/F-4009；原生 PG，仅 type=2）。
     *
     * <p>PG 把 epoch 秒 {@code created_at} 转日期再 group。返回列：{@code day(text) / quota(bigint) /
     * cnt(bigint) / model(model_name)}。{@code userId}/{@code username} 可空。</p>
     *
     * @return 按日升序的聚合行列表
     */
    @Select("""
            SELECT to_char(to_timestamp(created_at), 'YYYY-MM-DD') AS day,
                   COALESCE(SUM(quota), 0) AS quota,
                   COUNT(*) AS cnt,
                   model_name AS model
            FROM logs
            WHERE type = 2
              AND (CAST(#{userId} AS INTEGER) IS NULL OR user_id = #{userId})
              AND (CAST(#{username} AS VARCHAR) IS NULL OR username = #{username})
              AND (CAST(#{startTs} AS BIGINT) IS NULL OR created_at >= #{startTs})
              AND (CAST(#{endTs} AS BIGINT) IS NULL OR created_at <= #{endTs})
            GROUP BY day, model_name
            ORDER BY day ASC
            """)
    List<Map<String, Object>> aggregateQuotaByDay(@Param("userId") Integer userId,
                                                  @Param("username") String username,
                                                  @Param("startTs") Long startTs,
                                                  @Param("endTs") Long endTs);

    /**
     * 实时聚合用量排行（F-4010；原生 PG，按对外公开名 A 分组、消费量降序）。
     *
     * <p>窗口下界 {@code created_at >= #{since}}。只取 type=2，按 {@code resolved_public_model}(A)
     * 分组 SUM(quota) 降序——绝不按 B/渠道（可见性铁律）。返回列：{@code model(public_model) /
     * quota(used_quota)}。</p>
     *
     * @return 降序排行行列表（上限 lim）
     */
    @Select("""
            SELECT resolved_public_model AS model,
                   COALESCE(SUM(quota), 0) AS quota
            FROM logs
            WHERE type = 2
              AND created_at >= #{since}
              AND resolved_public_model <> ''
            GROUP BY resolved_public_model
            ORDER BY quota DESC
            LIMIT #{lim}
            """)
    List<Map<String, Object>> aggregateRanking(@Param("since") long since, @Param("lim") int lim);

    /**
     * 按对外公开名 A 聚合利润看板（F-6009 dimension=model；原生 PG，仅 type=2）。
     *
     * <p>按 {@code resolved_public_model}(A) 分组，SUM 售价/成本/利润，并统计成本缺失条数。空键剔除，
     * 利润降序。返回列：{@code dimkey / sell / cost / profit / missing / reqcount}（后五者 bigint）。</p>
     *
     * @return 利润聚合行列表（利润降序）
     */
    @Select("""
            SELECT resolved_public_model AS dimkey,
                   COALESCE(SUM(quota_sell), 0)   AS sell,
                   COALESCE(SUM(quota_cost), 0)   AS cost,
                   COALESCE(SUM(quota_profit), 0) AS profit,
                   COUNT(*) FILTER (WHERE quota_cost = 0 AND quota_sell > 0) AS missing,
                   COUNT(*) AS reqcount
            FROM logs
            WHERE type = 2
              AND resolved_public_model <> ''
              AND (CAST(#{startTs} AS BIGINT) IS NULL OR created_at >= #{startTs})
              AND (CAST(#{endTs} AS BIGINT) IS NULL OR created_at <= #{endTs})
            GROUP BY resolved_public_model
            ORDER BY profit DESC
            """)
    List<Map<String, Object>> aggregateProfitByModel(@Param("startTs") Long startTs, @Param("endTs") Long endTs);

    /**
     * 按渠道名聚合利润看板（F-6009 dimension=channel；原生 PG，仅 type=2）。
     *
     * <p>按 {@code channel_name} 分组（NULL/空合并为 'unknown'），口径同 {@link #aggregateProfitByModel}。</p>
     *
     * @return 利润聚合行列表（利润降序）
     */
    @Select("""
            SELECT COALESCE(NULLIF(channel_name, ''), 'unknown') AS dimkey,
                   COALESCE(SUM(quota_sell), 0)   AS sell,
                   COALESCE(SUM(quota_cost), 0)   AS cost,
                   COALESCE(SUM(quota_profit), 0) AS profit,
                   COUNT(*) FILTER (WHERE quota_cost = 0 AND quota_sell > 0) AS missing,
                   COUNT(*) AS reqcount
            FROM logs
            WHERE type = 2
              AND (CAST(#{startTs} AS BIGINT) IS NULL OR created_at >= #{startTs})
              AND (CAST(#{endTs} AS BIGINT) IS NULL OR created_at <= #{endTs})
            GROUP BY COALESCE(NULLIF(channel_name, ''), 'unknown')
            ORDER BY profit DESC
            """)
    List<Map<String, Object>> aggregateProfitByChannel(@Param("startTs") Long startTs, @Param("endTs") Long endTs);

    /**
     * 按用户分组聚合利润看板（F-6009 dimension=group；原生 PG，仅 type=2）。
     *
     * <p>按 {@code "group"} 分组（NULL/空合并为 'default'），口径同 {@link #aggregateProfitByModel}。
     * {@code group} 是 PG 保留字，须加双引号。</p>
     *
     * @return 利润聚合行列表（利润降序）
     */
    @Select("""
            SELECT COALESCE(NULLIF("group", ''), 'default') AS dimkey,
                   COALESCE(SUM(quota_sell), 0)   AS sell,
                   COALESCE(SUM(quota_cost), 0)   AS cost,
                   COALESCE(SUM(quota_profit), 0) AS profit,
                   COUNT(*) FILTER (WHERE quota_cost = 0 AND quota_sell > 0) AS missing,
                   COUNT(*) AS reqcount
            FROM logs
            WHERE type = 2
              AND (CAST(#{startTs} AS BIGINT) IS NULL OR created_at >= #{startTs})
              AND (CAST(#{endTs} AS BIGINT) IS NULL OR created_at <= #{endTs})
            GROUP BY COALESCE(NULLIF("group", ''), 'default')
            ORDER BY profit DESC
            """)
    List<Map<String, Object>> aggregateProfitByGroup(@Param("startTs") Long startTs, @Param("endTs") Long endTs);

    /**
     * 分批清理早于目标时间的日志（F-4006，每批上限 batchSize；原生 PG，ctid 限批 DELETE）。
     *
     * <p>PG 不支持 {@code DELETE ... LIMIT}，故以子查询选出 ≤ batchSize 行的 ctid 再删。
     * 调用方循环调用直到本批 &lt; batchSize（分批，避免一次锁全表）。</p>
     *
     * @param targetTimestamp 删 created_at &lt; 该值
     * @param batchSize       单批上限
     * @return 本批实际删除行数
     */
    @Delete("""
            DELETE FROM logs
            WHERE ctid IN (
                SELECT ctid FROM logs
                WHERE created_at < #{targetTimestamp}
                LIMIT #{batchSize}
            )
            """)
    int purgeBatch(@Param("targetTimestamp") long targetTimestamp, @Param("batchSize") int batchSize);

    /**
     * 匿名化某用户历史日志的 username 归属字段（F-5020 账号注销级联）。
     *
     * <p>把该 user_id 名下所有日志的 username 置为匿名占位，不删本体（保留计量/审计聚合价值）。
     * 批量 UPDATE，返回受影响行数。logs 表 user_id 为 int 列，入参窄化为 Integer。</p>
     *
     * @param userId 被注销用户 id（int 列）
     * @param anon   匿名占位用户名
     * @return 受影响（匿名化）行数
     */
    @Update("UPDATE logs SET username = #{anon} WHERE user_id = #{userId}")
    int anonymizeByUserId(@Param("userId") Integer userId, @Param("anon") String anon);
}
