package com.nexa.log.application;

import com.nexa.log.domain.repository.LogRepository;
import com.nexa.log.domain.vo.LogQuery;
import com.nexa.log.domain.vo.Pagination;
import org.springframework.stereotype.Service;

/**
 * 用户自助日志查询用例（应用层，F-4002 GET /api/log/self）。
 *
 * <p>用例编排：接收已强制 self-scope 的 {@link LogQuery}（{@link LogQuery#forSelf}，user_id 来自认证
 * 上下文、无 username/channel 维度）+ 分页，取当前页 + total。鉴权（UserAuth）由接口层拦截。
 * self-scope 由 query 结构保证（forSelf 钉死 userId 且无 username/channel/request_id 维度），杜绝越权。</p>
 *
 * <p>领域规则来源：prd 日志与用量 F-4002「仅本人 user_id；无 username/channel 过滤维度」。</p>
 */
@Service
public class QuerySelfLogsUseCase {

    private final LogRepository logRepository;

    /** @param logRepository 日志查询仓储 */
    public QuerySelfLogsUseCase(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * 分页查询当前用户日志（F-4002）。
     *
     * @param query      自助过滤条件（{@link LogQuery#forSelf}，已含 self-scope user_id）
     * @param pagination 分页参数
     * @return 当前页日志 + total + 分页（接口层裁剪为 UserLogView 列表）
     */
    public LogPage query(LogQuery query, Pagination pagination) {
        long total = logRepository.countByFilter(query);
        var items = logRepository.findByFilter(query, pagination.offset(), pagination.pageSize());
        return new LogPage(items, total, pagination);
    }
}
