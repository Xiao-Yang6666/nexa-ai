package com.nexa.log.domain.repository;

import com.nexa.log.domain.model.LogEntry;
import com.nexa.log.domain.vo.LogQuery;
import com.nexa.log.domain.vo.Period;
import com.nexa.log.domain.vo.ProfitDashboardEntry;
import com.nexa.log.domain.vo.ProfitDimension;
import com.nexa.log.domain.vo.QuotaDataPoint;
import com.nexa.log.domain.vo.RankingEntry;

import java.util.List;

/**
 * 日志查询仓储接口（domain 定接口，infrastructure 实现，backend-engineer §2.3）。
 *
 * <p>日志与用量 BC 的读侧 + 审计写侧 + 历史清理统一抽象。relay BC 负责消费/错误日志的<b>写入</b>
 * （{@code RelayLogRepository.save}），本接口负责<b>查询/统计/配额按日/排行/审计写/清理</b>
 * （F-4001~F-4013）。两者读写分离、各持仓储接口（CQRS 风格），底层同一张 logs 表（V11）。</p>
 *
 * <p>所有方法只依赖领域类型（{@link LogQuery}/{@link LogEntry}/{@link QuotaDataPoint} 等），
 * 不漏任何 JPA/ORM 类型——可被纯单测 mock。</p>
 */
public interface LogRepository {

    /**
     * 按过滤条件分页查询日志（F-4001 管理端 / F-4002 自助），按 created_at,id 降序（新→旧）。
     *
     * @param query  过滤条件（含可空 user_id self-scope）
     * @param offset 起始偏移
     * @param limit  每页条数
     * @return 当前页日志领域对象（新→旧）
     */
    List<LogEntry> findByFilter(LogQuery query, int offset, int limit);

    /**
     * 统计满足过滤条件的日志总条数（F-4001/F-4002 分页 total）。
     *
     * @param query 过滤条件
     * @return 总条数
     */
    long countByFilter(LogQuery query);

    /**
     * 按令牌 id 查询其消费日志（F-4003，tokenReadAuth；按 created_at,id 降序，默认上限由实现约束）。
     *
     * @param tokenId 令牌 id（&gt;0，调用方已校验非 0）
     * @param limit   返回条数上限
     * @return 该令牌的消费日志（新→旧）
     */
    List<LogEntry> findByTokenId(long tokenId, int limit);

    /**
     * 聚合消费日志的 quota/请求数/token 数（F-4004/F-4005 统计原料；仅 Type=2）。
     *
     * <p>返回 {@code long[3]}：{@code [requestCount, quotaSum, tokenSum]}，由用例交给
     * {@link com.nexa.log.domain.vo.LogStat#of} 算出 rpm/tpm（速率换算在领域值对象，不在 SQL）。</p>
     *
     * @param query 过滤条件（实现会强制仅 Type=2 Consume）
     * @return [请求数, quota 总和, token 总和]
     */
    long[] aggregateConsume(LogQuery query);

    /**
     * 按日聚合配额数据（F-4007 管理端 / F-4008 按用户 / F-4009 自助；仅消费记录）。
     *
     * <p>按 {@code (自然日, model_name)} 分组，产出 {@link QuotaDataPoint} 列表。
     * {@code userId==null} 表示全站（管理端 username 维度由 query.username 控制），非空则限定本人。</p>
     *
     * @param query 过滤条件（含时间区间、可空 user_id/username）
     * @return 按日配额聚合项（按日期升序）
     */
    List<QuotaDataPoint> aggregateQuotaByDay(LogQuery query);

    /**
     * 实时聚合用量排行（F-4010；按对外公开名 A 分组，period 回看窗口内消费量降序）。
     *
     * @param period   统计周期（含回看窗口秒数）
     * @param nowEpoch 当前 epoch 秒（窗口下界 = now - lookback）
     * @param limit    排行条数上限
     * @return 排行条目（rank 1 起，按用量降序）
     */
    List<RankingEntry> aggregateRanking(Period period, long nowEpoch, int limit);

    /**
     * 按维度聚合利润看板（F-6009 GET /api/profit/dashboard；仅消费 type=2，admin/root 视图）。
     *
     * <p>按 {@link ProfitDimension} 指定列（model→对外名 A / channel→渠道名 / group→用户分组）分组，对
     * {@code quota_sell / quota_cost / quota_profit} 求和，并统计成本缺失条数（{@code quota_cost=0 且
     * quota_sell>0}）。利润率为派生量在领域算（见 {@link ProfitDashboardEntry#profitRate()}）。
     * 按利润降序返回。</p>
     *
     * @param dimension 聚合维度（受控枚举，决定 GROUP BY 列；非外部裸串）
     * @param startTs   起始 epoch 秒（可空=不限）
     * @param endTs     结束 epoch 秒（可空=不限）
     * @return 各维度键的利润聚合项（按利润降序）
     */
    List<ProfitDashboardEntry> aggregateProfit(ProfitDimension dimension, Long startTs, Long endTs);

    /**
     * 写入一条审计日志（F-4011/F-4012/F-4013，Type=3/7）。
     *
     * @param entry 审计日志聚合（由 LogEntry.manageAudit/securityAudit/loginAudit 工厂构造）
     */
    void recordAudit(LogEntry entry);

    /**
     * 分批清理早于目标时间的历史日志（F-4006，每批上限 100）。
     *
     * @param targetTimestamp 删除 created_at &lt; 该值的日志（epoch 秒，调用方已校验 &gt;0）
     * @param batchSize       单批删除上限（契约：100）
     * @return 实际删除条数
     */
    int purgeOlderThan(long targetTimestamp, int batchSize);

    /**
     * 匿名化某用户的历史日志归属（F-5020 账号注销级联，DC-003/DC-011）。
     *
     * <p>账号注销时调用：把该用户名下所有历史日志的「可识别个人信息归属字段」做匿名化处置——
     * 把 {@code username} 字段置为不可逆匿名占位（如 {@code deleted_<id>}），<b>不删日志本体</b>。
     * 这样既满足「注销后 PII 清空或匿名」（验收标准），又保留日志中不含个人信息的计量/审计价值
     * （消费量、模型分布等聚合统计仍可用，DC-001 计量级按聚合保留）。</p>
     *
     * <p>领域规则来源：API-ENDPOINTS §14.5 F-5020「…并匿名化日志」。注意 logs 表 user_id 为整数列，
     * 此处保留 user_id（用于级联定位与聚合归集），仅把可读 username 这一 PII 字段匿名化——user_id 本身
     * 在账号已注销后不再能反查到自然人（用户聚合已匿名化 + 软删）。幂等：重复匿名化无害。</p>
     *
     * @param userId             被注销用户的 id
     * @param anonymizedUsername 匿名占位用户名（如 {@code deleted_<id>}，与用户聚合匿名占位一致）
     * @return 实际匿名化的日志条数
     */
    long anonymizeUserLogs(long userId, String anonymizedUsername);
}
