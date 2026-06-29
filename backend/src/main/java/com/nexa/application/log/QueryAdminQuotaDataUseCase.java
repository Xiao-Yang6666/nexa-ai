package com.nexa.application.log;

import com.nexa.domain.log.repository.LogRepository;
import com.nexa.domain.log.vo.LogQuery;
import com.nexa.domain.log.vo.QuotaDataPoint;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端配额按日数据查询用例（应用层，F-4007 GET /api/data/ + F-4008 按用户聚合）。
 *
 * <p>用例编排：构造管理端配额查询条件（可按 username 过滤指定用户，空 username=全站），按时间区间
 * 调仓储按日聚合。F-4008（GetQuotaDatesByUser，全站用户维度聚合）属同一聚合查询的「不传 username =
 * 全站、按 user/日分组」分支，由仓储 SQL 的 username 可空 + 按 (日,model) 分组覆盖；本用例统一承载。</p>
 *
 * <p>领域规则来源：prd 日志与用量 F-4007「传 username 过滤指定用户；返回按日分组数据；空 username 返回全站」
 * + F-4008「全站用户维度聚合」。鉴权 AdminAuth 由接口层拦截。</p>
 */
@Service
public class QueryAdminQuotaDataUseCase {

    private final LogRepository logRepository;

    /** @param logRepository 日志查询仓储 */
    public QueryAdminQuotaDataUseCase(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * 管理端按日配额数据（F-4007/F-4008）。
     *
     * @param start    起始 epoch 秒（可空=不限）
     * @param end      结束 epoch 秒（可空=不限）
     * @param username 指定用户名过滤（空/null=全站）
     * @return 按日 + 模型聚合的配额数据项（按日期升序）
     */
    public List<QuotaDataPoint> query(Long start, Long end, String username) {
        // 管理端配额查询无 self-scope（userId=null=全站），username 可选限定某用户。
        LogQuery query = LogQuery.forAdmin(2, start, end, username,
                null, null, null, null, null, null);
        return logRepository.aggregateQuotaByDay(query);
    }
}
