package com.nexa.log.application;

import com.nexa.log.domain.repository.LogRepository;
import com.nexa.log.domain.vo.LogQuery;
import com.nexa.log.domain.vo.Pagination;
import org.springframework.stereotype.Service;

/**
 * 管理端全量日志查询用例（应用层，F-4001 GET /api/log/）。
 *
 * <p>用例编排：接收已构造好的管理端 {@link LogQuery}（不绑定 user_id，可全维度过滤）+ 分页，
 * 调仓储取当前页 + total，封装为 {@link LogPage}。鉴权（AdminAuth）由接口层 {@code @RequireRole(ADMIN)}
 * 拦截，本用例不重复校验角色（薄应用层，backend-engineer §2.1）。</p>
 *
 * <p>领域规则来源：prd 日志与用量 F-4001「按类型/时间/用户名/令牌名/模型/渠道/分组/请求ID 过滤；
 * total 与 page_size 分页正确」。</p>
 */
@Service
public class QueryAdminLogsUseCase {

    private final LogRepository logRepository;

    /** @param logRepository 日志查询仓储 */
    public QueryAdminLogsUseCase(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * 分页查询管理端日志（F-4001）。
     *
     * @param query      管理端过滤条件（{@link LogQuery#forAdmin}）
     * @param pagination 分页参数
     * @return 当前页日志 + total + 分页（接口层裁剪为 AdminLogView 列表）
     */
    public LogPage query(LogQuery query, Pagination pagination) {
        long total = logRepository.countByFilter(query);
        var items = logRepository.findByFilter(query, pagination.offset(), pagination.pageSize());
        return new LogPage(items, total, pagination);
    }
}
