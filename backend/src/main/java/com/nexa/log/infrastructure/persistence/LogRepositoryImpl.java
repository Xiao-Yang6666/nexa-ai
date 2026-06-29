package com.nexa.log.infrastructure.persistence;

import com.nexa.log.domain.exception.LogPersistenceException;
import com.nexa.log.domain.model.LogEntry;
import com.nexa.log.domain.repository.LogRepository;
import com.nexa.log.domain.vo.LogQuery;
import com.nexa.log.domain.vo.LogType;
import com.nexa.log.domain.vo.Period;
import com.nexa.log.domain.vo.ProfitDashboardEntry;
import com.nexa.log.domain.vo.ProfitDimension;
import com.nexa.log.domain.vo.QuotaDataPoint;
import com.nexa.log.domain.vo.RankingEntry;
import com.nexa.log.infrastructure.persistence.entity.LogReadJpaEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志查询仓储 JPA 实现（基础设施层适配器，F-4001~F-4013）。
 *
 * <p>DDD 依赖倒置：domain 定 {@link LogRepository} 接口，本类用 {@link SpringDataLogReadRepository}
 * + 实体映射实现。{@code userId}/{@code channelId}/{@code tokenId} 在领域为 Long（与 account/channel BC
 * 主键一致），logs 表为 int 列（现网兼容），映射时窄化（值域受控）。所有数据访问异常 wrap 为
 * {@link LogPersistenceException} 带操作上下文上抛（backend-engineer §3.2 不吞错）。</p>
 */
@Repository
public class LogRepositoryImpl implements LogRepository {

    /** 按令牌 key 查日志的默认返回上限（F-4003，防一次性拉全令牌历史）。 */
    private static final int TOKEN_LOG_LIMIT = 100;

    /** 排行榜默认条目上限（F-4010）。 */
    private static final int RANKING_LIMIT = 50;

    private final SpringDataLogReadRepository jpa;

    /** @param jpa Spring Data 日志读仓库 */
    public LogRepositoryImpl(SpringDataLogReadRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<LogEntry> findByFilter(LogQuery query, int offset, int limit) {
        try {
            // PageRequest 以「页号 = offset/limit」承载偏移（limit 已归一 ≤100）。
            int page = limit > 0 ? offset / limit : 0;
            List<LogReadJpaEntity> rows = jpa.findByFilter(
                    toInt(query.userId()),
                    typeCode(query.typeFilter()),
                    query.startTimestamp(),
                    query.endTimestamp(),
                    query.username(),
                    query.tokenName(),
                    query.modelName(),
                    toInt(query.channelId()),
                    query.group(),
                    query.requestId(),
                    query.upstreamRequestId(),
                    PageRequest.of(page, limit));
            List<LogEntry> result = new ArrayList<>(rows.size());
            for (LogReadJpaEntity e : rows) {
                result.add(toDomain(e));
            }
            return result;
        } catch (DataAccessException ex) {
            throw new LogPersistenceException("list logs by filter", ex);
        }
    }

    @Override
    public long countByFilter(LogQuery query) {
        try {
            return jpa.countByFilter(
                    toInt(query.userId()),
                    typeCode(query.typeFilter()),
                    query.startTimestamp(),
                    query.endTimestamp(),
                    query.username(),
                    query.tokenName(),
                    query.modelName(),
                    toInt(query.channelId()),
                    query.group(),
                    query.requestId(),
                    query.upstreamRequestId());
        } catch (DataAccessException ex) {
            throw new LogPersistenceException("count logs by filter", ex);
        }
    }

    @Override
    public List<LogEntry> findByTokenId(long tokenId, int limit) {
        try {
            int cap = limit > 0 ? limit : TOKEN_LOG_LIMIT;
            List<LogReadJpaEntity> rows = jpa.findConsumeByToken((int) tokenId, PageRequest.of(0, cap));
            List<LogEntry> result = new ArrayList<>(rows.size());
            for (LogReadJpaEntity e : rows) {
                result.add(toDomain(e));
            }
            return result;
        } catch (DataAccessException ex) {
            throw new LogPersistenceException("find logs by token id", ex);
        }
    }

    @Override
    public long[] aggregateConsume(LogQuery query) {
        try {
            // 强制仅消费类（asConsumeOnly 已在用例侧调用，此处仍按 type=2 SQL 兜底）。
            Object[] row = jpa.aggregateConsume(
                    toInt(query.userId()),
                    query.startTimestamp(),
                    query.endTimestamp(),
                    query.username(),
                    query.tokenName(),
                    query.modelName(),
                    query.group());
            // JPQL 聚合单行返回 Object[3]；某些驱动包一层 → 取最内层。
            Object[] cols = unwrapAggregateRow(row);
            long count = toLong(cols[0]);
            long quota = toLong(cols[1]);
            long tokens = toLong(cols[2]);
            return new long[]{count, quota, tokens};
        } catch (DataAccessException ex) {
            throw new LogPersistenceException("aggregate consume stat", ex);
        }
    }

    @Override
    public List<QuotaDataPoint> aggregateQuotaByDay(LogQuery query) {
        try {
            List<Object[]> rows = jpa.aggregateQuotaByDay(
                    toInt(query.userId()),
                    query.username(),
                    query.startTimestamp(),
                    query.endTimestamp());
            List<QuotaDataPoint> result = new ArrayList<>(rows.size());
            for (Object[] r : rows) {
                result.add(new QuotaDataPoint(
                        (String) r[0],
                        toLong(r[1]),
                        toLong(r[2]),
                        (String) r[3]));
            }
            return result;
        } catch (DataAccessException ex) {
            throw new LogPersistenceException("aggregate quota by day", ex);
        }
    }

    @Override
    public List<RankingEntry> aggregateRanking(Period period, long nowEpoch, int limit) {
        try {
            long since = nowEpoch - period.lookbackSeconds();
            int cap = limit > 0 ? limit : RANKING_LIMIT;
            List<Object[]> rows = jpa.aggregateRanking(since, cap);
            List<RankingEntry> result = new ArrayList<>(rows.size());
            int rank = 1;
            for (Object[] r : rows) {
                result.add(new RankingEntry(
                        rank++,
                        (String) r[0],
                        toLong(r[1]),
                        period.wireValue(),
                        nowEpoch));
            }
            return result;
        } catch (DataAccessException ex) {
            throw new LogPersistenceException("aggregate ranking", ex);
        }
    }

    @Override
    public List<ProfitDashboardEntry> aggregateProfit(ProfitDimension dimension, Long startTs, Long endTs) {
        try {
            // 维度→对应固定 SQL（每个维度的 GROUP BY 列是受控常量列名，无注入面；维度枚举已在领域归一）。
            List<Object[]> rows = switch (dimension) {
                case MODEL -> jpa.aggregateProfitByModel(startTs, endTs);
                case CHANNEL -> jpa.aggregateProfitByChannel(startTs, endTs);
                case GROUP -> jpa.aggregateProfitByGroup(startTs, endTs);
            };
            List<ProfitDashboardEntry> result = new ArrayList<>(rows.size());
            for (Object[] r : rows) {
                // 列序：dimension_key, sum_sell, sum_cost, sum_profit, cost_missing_count, request_count
                result.add(new ProfitDashboardEntry(
                        r[0] == null ? "" : r[0].toString(),
                        toLong(r[1]),
                        toLong(r[2]),
                        toLong(r[3]),
                        toLong(r[4]),
                        toLong(r[5])));
            }
            return result;
        } catch (DataAccessException ex) {
            throw new LogPersistenceException("aggregate profit dashboard", ex);
        }
    }

    @Override
    public void recordAudit(LogEntry entry) {
        try {
            LogReadJpaEntity e = new LogReadJpaEntity();
            e.setUserId(toInt(entry.userId()));
            e.setCreatedAt(entry.createdAt());
            e.setType(entry.type() == null ? 0 : entry.type().code());
            e.setContent(entry.content());
            e.setUsername(entry.username());
            e.setIp(entry.ip());
            // 审计日志不涉及 token/model/quota 等消费字段，留默认（null/0），仅记 who/what/when/where。
            jpa.save(e);
        } catch (DataAccessException ex) {
            throw new LogPersistenceException("record audit log", ex);
        }
    }

    @Override
    public int purgeOlderThan(long targetTimestamp, int batchSize) {
        try {
            int total = 0;
            int deleted;
            // 分批循环删除直到无更多（每批 ≤ batchSize，避免一次锁全表，F-4006）。
            do {
                deleted = jpa.purgeBatch(targetTimestamp, batchSize);
                total += deleted;
            } while (deleted >= batchSize);
            return total;
        } catch (DataAccessException ex) {
            throw new LogPersistenceException("purge logs older than target", ex);
        }
    }

    @Override
    public long anonymizeUserLogs(long userId, String anonymizedUsername) {
        try {
            // user_id 在 logs 表为 int 列，窄化（值域受控：account 主键不会超 int 范围）。
            return jpa.anonymizeByUserId((int) userId, anonymizedUsername);
        } catch (DataAccessException ex) {
            // 不吞错：注销级联的日志匿名化失败必须上抛，由用例事务整体回滚（避免半注销）。
            throw new LogPersistenceException("anonymize user logs for deactivation", ex);
        }
    }

    // ===================== 映射与窄化 =====================

    private LogEntry toDomain(LogReadJpaEntity e) {
        return LogEntry.rebuild()
                .id(e.getId())
                .userId(toLong(e.getUserId()))
                .createdAt(e.getCreatedAt() == null ? 0L : e.getCreatedAt())
                .type(LogType.fromCode(e.getType() == null ? 0 : e.getType()))
                .content(e.getContent())
                .username(e.getUsername())
                .tokenName(e.getTokenName())
                .modelName(e.getModelName())
                .quota(e.getQuota() == null ? 0L : e.getQuota())
                .promptTokens(e.getPromptTokens() == null ? 0 : e.getPromptTokens())
                .completionTokens(e.getCompletionTokens() == null ? 0 : e.getCompletionTokens())
                .useTime(e.getUseTime() == null ? 0 : e.getUseTime())
                .stream(Boolean.TRUE.equals(e.getIsStream()))
                .channelId(toLong(e.getChannelId()))
                .channelName(e.getChannelName())
                .tokenId(toLong(e.getTokenId()))
                .group(e.getGroup())
                .ip(e.getIp())
                .requestId(e.getRequestId())
                .upstreamRequestId(e.getUpstreamRequestId())
                .other(e.getOther())
                .requestedModel(e.getRequestedModel())
                .resolvedPublicModel(e.getResolvedPublicModel())
                .actualUpstreamModel(e.getActualUpstreamModel())
                .inboundProtocol(e.getInboundProtocol())
                .upstreamProtocol(e.getUpstreamProtocol())
                .protocolConverted(Boolean.TRUE.equals(e.getProtocolConverted()))
                .userAgent(e.getUserAgent())
                .quotaSell(e.getQuotaSell() == null ? 0 : e.getQuotaSell())
                .quotaCost(e.getQuotaCost() == null ? 0 : e.getQuotaCost())
                .quotaProfit(e.getQuotaProfit() == null ? 0 : e.getQuotaProfit())
                .build();
    }

    private Integer toInt(Long v) {
        return v == null ? null : v.intValue();
    }

    private Long toLong(Integer v) {
        return v == null ? null : v.longValue();
    }

    private Integer typeCode(LogType t) {
        // null typeFilter = 不按类型过滤（传 null 让 :type IS NULL 命中全部）。
        return t == null ? null : t.code();
    }

    /**
     * 把 JPQL 聚合单行结果归一为 Object[3]。
     *
     * <p>{@code SELECT COUNT,SUM,SUM} 返回单行多列，Spring Data 投影为 {@code Object[]}；
     * 个别情况下会包成 {@code Object[]{Object[]}}（单行被外层数组包裹），此处统一解包到内层三列数组。</p>
     */
    private Object[] unwrapAggregateRow(Object[] row) {
        if (row != null && row.length == 1 && row[0] instanceof Object[] inner) {
            return inner;
        }
        return row;
    }

    private long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(o.toString());
    }
}
