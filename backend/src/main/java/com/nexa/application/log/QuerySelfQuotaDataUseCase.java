package com.nexa.application.log;

import com.nexa.domain.log.repository.LogRepository;
import com.nexa.domain.log.vo.LogQuery;
import com.nexa.domain.log.vo.QuotaDataPoint;
import com.nexa.domain.log.vo.TimeRange;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 用户自助配额按日数据查询用例（应用层，F-4009 GET /api/data/self）。
 *
 * <p>用例编排：用 {@link TimeRange#boundedToOneMonth} 把时间区间约束在 1 个月内（领域护栏，超出抛
 * {@code InvalidLogQueryException}「时间跨度不能超过 1 个月」），再以 forSelf 强制 user_id 过滤
 * （来自认证上下文，防越权读他人），按日聚合返回本人配额数据。</p>
 *
 * <p>领域规则来源：prd 日志与用量 F-4009「时间跨度超过 2592000 秒(1 月)返回错误；仅返回当前 userId 数据」。</p>
 */
@Service
public class QuerySelfQuotaDataUseCase {

    private final LogRepository logRepository;

    /** @param logRepository 日志查询仓储 */
    public QuerySelfQuotaDataUseCase(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * 自助按日配额数据（F-4009，跨度上限 1 月，强制 self-scope）。
     *
     * @param userId 当前认证用户 id（来自上下文，防伪造）
     * @param start  起始 epoch 秒（可空→end-1月）
     * @param end    结束 epoch 秒（可空→now）
     * @return 本人按日 + 模型聚合的配额数据项（按日期升序）
     * @throws com.nexa.domain.log.exception.InvalidLogQueryException 时间跨度超 1 个月
     */
    public List<QuotaDataPoint> query(long userId, Long start, Long end) {
        long now = Instant.now().getEpochSecond();
        // 领域护栏：1 个月跨度上限 + 缺省窗口归一（超限即抛 InvalidLogQueryException）。
        TimeRange range = TimeRange.boundedToOneMonth(start, end, now);
        // forSelf 钉死 user_id + 无 username/channel 维度，杜绝越权。
        LogQuery query = LogQuery.forSelf(userId, 2, range.start(), range.end(), null, null, null);
        return logRepository.aggregateQuotaByDay(query);
    }
}
