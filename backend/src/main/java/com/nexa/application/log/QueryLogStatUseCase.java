package com.nexa.application.log;

import com.nexa.domain.log.repository.LogRepository;
import com.nexa.domain.log.vo.LogQuery;
import com.nexa.domain.log.vo.LogStat;
import org.springframework.stereotype.Service;

/**
 * 日志统计用例（应用层，F-4004 管理端 GET /api/log/stat / F-4005 自助 GET /api/log/self/stat）。
 *
 * <p>用例编排：接收过滤条件（管理端可带 username；自助由 forSelf 钉死 user_id 且 username 来自上下文
 * 不可伪造），强制仅统计消费类（{@link LogQuery#asConsumeOnly}），从仓储取聚合原料
 * {@code [请求数, quota 总和, token 总和]}，交给 {@link LogStat#of} 算 quota/rpm/tpm（速率换算在领域值对象）。</p>
 *
 * <p>领域规则来源：prd 日志与用量 F-4004/F-4005「返回 quota/rpm/tpm；仅统计 type=2(LogTypeConsume)」。
 * rpm/tpm 的时间窗用查询的 start/end 推导；未传时间窗时 {@link LogStat#of} 兜底分钟数为 1（总量≈速率）。</p>
 */
@Service
public class QueryLogStatUseCase {

    private final LogRepository logRepository;

    /** @param logRepository 日志查询仓储 */
    public QueryLogStatUseCase(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * 统计消费日志的 quota/rpm/tpm（F-4004 管理端 / F-4005 自助，仅 Type=2）。
     *
     * @param query 过滤条件（任意视图；本用例强制 asConsumeOnly）
     * @return 统计结果值对象
     */
    public LogStat stat(LogQuery query) {
        LogQuery consumeOnly = query.asConsumeOnly();
        long[] agg = logRepository.aggregateConsume(consumeOnly);
        long requestCount = agg[0];
        long quotaSum = agg[1];
        long tokenSum = agg[2];
        long windowSeconds = windowSeconds(query.startTimestamp(), query.endTimestamp());
        return LogStat.of(requestCount, quotaSum, tokenSum, windowSeconds);
    }

    /** 计算统计时间窗秒数（start/end 均有才有窗，否则 0 → 值对象兜底分钟数 1）。 */
    private long windowSeconds(Long start, Long end) {
        if (start == null || end == null || end <= start) {
            return 0L;
        }
        return end - start;
    }
}
