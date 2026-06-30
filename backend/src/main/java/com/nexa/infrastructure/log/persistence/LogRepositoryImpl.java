package com.nexa.infrastructure.log.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexa.domain.log.exception.LogPersistenceException;
import com.nexa.domain.log.model.LogEntry;
import com.nexa.domain.log.repository.LogRepository;
import com.nexa.domain.log.vo.LogQuery;
import com.nexa.domain.log.vo.LogType;
import com.nexa.domain.log.vo.Period;
import com.nexa.domain.log.vo.ProfitDashboardEntry;
import com.nexa.domain.log.vo.ProfitDimension;
import com.nexa.domain.log.vo.QuotaDataPoint;
import com.nexa.domain.log.vo.RankingEntry;
import com.nexa.infrastructure.log.persistence.po.LogReadPO;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 日志查询仓储 {@link LogRepository} 的 MyBatis-Plus 实现（基础设施层适配器，F-4001~F-4013）。
 *
 * <p>DDD 依赖倒置：domain 定 {@link LogRepository} 接口，本类用 {@link LogReadMapper} +
 * PO 就近工厂方法（{@code PO.toDomain} / {@code PO.ofAudit}）实现。等值多条件过滤用
 * {@code LambdaQueryWrapper} 组装，PG 原生聚合 / 分批清理 / 批量匿名化下沉到 Mapper 注解方法。
 * {@code userId}/{@code channelId}/{@code tokenId} 在领域为 Long（与 account/channel BC 主键一致），
 * logs 表为 int 列（现网兼容），映射时窄化（值域受控）。所有数据访问异常 wrap 为
 * {@link LogPersistenceException} 带操作上下文上抛（backend-engineer §3.2 不吞错）。</p>
 */
@Repository
public class LogRepositoryImpl implements LogRepository {

    /** 按令牌 key 查日志的默认返回上限（F-4003，防一次性拉全令牌历史）。 */
    private static final int TOKEN_LOG_LIMIT = 100;

    /** 排行榜默认条目上限（F-4010）。 */
    private static final int RANKING_LIMIT = 50;

    private final LogReadMapper mapper;

    /** @param mapper MyBatis-Plus 日志读 Mapper（infra 内部依赖） */
    public LogRepositoryImpl(LogReadMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<LogEntry> findByFilter(LogQuery query, int offset, int limit) {
        try {
            // 以「页号 = offset/limit」承载偏移（limit 已归一 ≤100）；MP 页号从 1 起，故 +1。
            int page0Based = limit > 0 ? offset / limit : 0;
            Page<LogReadPO> page = Page.of(page0Based + 1, limit > 0 ? limit : 1);
            LambdaQueryWrapper<LogReadPO> w = filterWrapper(query)
                    .orderByDesc(LogReadPO::getCreatedAt)
                    .orderByDesc(LogReadPO::getId);
            List<LogReadPO> rows = mapper.selectPage(page, w).getRecords();
            List<LogEntry> result = new ArrayList<>(rows.size());
            for (LogReadPO e : rows) {
                result.add(e.toDomain());
            }
            return result;
        } catch (DataAccessException ex) {
            throw new LogPersistenceException("list logs by filter", ex);
        }
    }

    @Override
    public long countByFilter(LogQuery query) {
        try {
            return mapper.selectCount(filterWrapper(query));
        } catch (DataAccessException ex) {
            throw new LogPersistenceException("count logs by filter", ex);
        }
    }

    @Override
    public List<LogEntry> findByTokenId(long tokenId, int limit) {
        try {
            int cap = limit > 0 ? limit : TOKEN_LOG_LIMIT;
            // cap 为可信整数（>0），last("LIMIT n") 无注入风险。仅消费类（type=2），新→旧。
            LambdaQueryWrapper<LogReadPO> w = Wrappers.<LogReadPO>lambdaQuery()
                    .eq(LogReadPO::getTokenId, (int) tokenId)
                    .eq(LogReadPO::getType, 2)
                    .orderByDesc(LogReadPO::getCreatedAt)
                    .orderByDesc(LogReadPO::getId)
                    .last("LIMIT " + cap);
            List<LogReadPO> rows = mapper.selectList(w);
            List<LogEntry> result = new ArrayList<>(rows.size());
            for (LogReadPO e : rows) {
                result.add(e.toDomain());
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
            Map<String, Object> row = mapper.aggregateConsume(
                    toInt(query.userId()),
                    query.startTimestamp(),
                    query.endTimestamp(),
                    query.username(),
                    query.tokenName(),
                    query.modelName(),
                    query.group());
            long count = toLong(row.get("cnt"));
            long quota = toLong(row.get("quota"));
            long tokens = toLong(row.get("tokens"));
            return new long[]{count, quota, tokens};
        } catch (DataAccessException ex) {
            throw new LogPersistenceException("aggregate consume stat", ex);
        }
    }

    @Override
    public List<QuotaDataPoint> aggregateQuotaByDay(LogQuery query) {
        try {
            List<Map<String, Object>> rows = mapper.aggregateQuotaByDay(
                    toInt(query.userId()),
                    query.username(),
                    query.startTimestamp(),
                    query.endTimestamp());
            List<QuotaDataPoint> result = new ArrayList<>(rows.size());
            for (Map<String, Object> r : rows) {
                result.add(new QuotaDataPoint(
                        (String) r.get("day"),
                        toLong(r.get("quota")),
                        toLong(r.get("cnt")),
                        (String) r.get("model")));
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
            List<Map<String, Object>> rows = mapper.aggregateRanking(since, cap);
            List<RankingEntry> result = new ArrayList<>(rows.size());
            int rank = 1;
            for (Map<String, Object> r : rows) {
                result.add(new RankingEntry(
                        rank++,
                        (String) r.get("model"),
                        toLong(r.get("quota")),
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
            List<Map<String, Object>> rows = switch (dimension) {
                case MODEL -> mapper.aggregateProfitByModel(startTs, endTs);
                case CHANNEL -> mapper.aggregateProfitByChannel(startTs, endTs);
                case GROUP -> mapper.aggregateProfitByGroup(startTs, endTs);
            };
            List<ProfitDashboardEntry> result = new ArrayList<>(rows.size());
            for (Map<String, Object> r : rows) {
                Object key = r.get("dimkey");
                result.add(new ProfitDashboardEntry(
                        key == null ? "" : key.toString(),
                        toLong(r.get("sell")),
                        toLong(r.get("cost")),
                        toLong(r.get("profit")),
                        toLong(r.get("missing")),
                        toLong(r.get("reqcount"))));
            }
            return result;
        } catch (DataAccessException ex) {
            throw new LogPersistenceException("aggregate profit dashboard", ex);
        }
    }

    @Override
    public void recordAudit(LogEntry entry) {
        try {
            // 审计日志不涉及 token/model/quota 等消费字段，仅记 who/what/when/where（见 PO.ofAudit）。
            mapper.insert(LogReadPO.ofAudit(entry));
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
                deleted = mapper.purgeBatch(targetTimestamp, batchSize);
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
            return mapper.anonymizeByUserId((int) userId, anonymizedUsername);
        } catch (DataAccessException ex) {
            // 不吞错：注销级联的日志匿名化失败必须上抛，由用例事务整体回滚（避免半注销）。
            throw new LogPersistenceException("anonymize user logs for deactivation", ex);
        }
    }

    // ===================== 过滤组装与窄化 =====================

    /**
     * 多条件过滤 wrapper（findByFilter/countByFilter 共用，维度一致）。
     *
     * <p>各维度可空（null=该维度不过滤，等价原 JPQL {@code :p IS NULL OR ...}）：MyBatis-Plus 的
     * {@code eq(condition, col, val)} 在 condition 为 false 时跳过该条件，语义等价。模型按
     * {@code model_name}(=C) 口径过滤（与现网报表一致）。</p>
     */
    private LambdaQueryWrapper<LogReadPO> filterWrapper(LogQuery query) {
        Integer userId = toInt(query.userId());
        Integer type = typeCode(query.typeFilter());
        Long startTs = query.startTimestamp();
        Long endTs = query.endTimestamp();
        String username = query.username();
        String tokenName = query.tokenName();
        String modelName = query.modelName();
        Integer channelId = toInt(query.channelId());
        String grp = query.group();
        String requestId = query.requestId();
        String upstreamRequestId = query.upstreamRequestId();
        return Wrappers.<LogReadPO>lambdaQuery()
                .eq(userId != null, LogReadPO::getUserId, userId)
                .eq(type != null, LogReadPO::getType, type)
                .ge(startTs != null, LogReadPO::getCreatedAt, startTs)
                .le(endTs != null, LogReadPO::getCreatedAt, endTs)
                .eq(username != null, LogReadPO::getUsername, username)
                .eq(tokenName != null, LogReadPO::getTokenName, tokenName)
                .eq(modelName != null, LogReadPO::getModelName, modelName)
                .eq(channelId != null, LogReadPO::getChannelId, channelId)
                .eq(grp != null, LogReadPO::getGroup, grp)
                .eq(requestId != null, LogReadPO::getRequestId, requestId)
                .eq(upstreamRequestId != null, LogReadPO::getUpstreamRequestId, upstreamRequestId);
    }

    private Integer toInt(Long v) {
        return v == null ? null : v.intValue();
    }

    private Integer typeCode(LogType t) {
        // null typeFilter = 不按类型过滤（不追加 type 条件，命中全部）。
        return t == null ? null : t.code();
    }

    /**
     * 把聚合 Map 列值归一为 long（PG COUNT/SUM 经驱动可能为 Long/BigInteger/BigDecimal，统一收口）。
     *
     * @param o 列值（可空）
     * @return 归一后的 long（null→0）
     */
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
