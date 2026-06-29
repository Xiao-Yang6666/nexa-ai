package com.nexa.application.log;

import com.nexa.domain.log.repository.LogRepository;
import com.nexa.domain.log.vo.Period;
import com.nexa.domain.log.vo.RankingEntry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 用量排行榜查询用例（应用层，F-4010 GET /api/rankings，公开/可选 UserAuth）。
 *
 * <p>用例编排：解析 period（{@link Period#parse}，缺省 week，非法→400 invalid period），按周期回看窗口
 * 实时聚合 logs 中消费记录，按对外公开名 A 分组降序产出排行。可见性铁律：排行只按 A、只暴露聚合用量，
 * 绝不含成本/利润/B/供应商（接口层用 RankingPublicView 序列化保证，仓储 SQL 也只 group by A）。</p>
 *
 * <p>领域规则来源：prd 日志与用量 F-4010「period 默认 week；非法 period 返回 400；成功返回排行快照」。</p>
 */
@Service
public class QueryRankingUseCase {

    private final LogRepository logRepository;

    /** @param logRepository 日志查询仓储 */
    public QueryRankingUseCase(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * 查询指定周期的用量排行（F-4010）。
     *
     * @param rawPeriod 原始 period 参数（null/空白→week；非 week/month→400）
     * @return 排行条目（rank 1 起，按用量降序；PublicView 口径）
     * @throws com.nexa.domain.log.exception.InvalidLogQueryException period 非法
     */
    public List<RankingEntry> query(String rawPeriod) {
        Period period = Period.parse(rawPeriod);
        long now = Instant.now().getEpochSecond();
        return logRepository.aggregateRanking(period, now, 0);
    }
}
