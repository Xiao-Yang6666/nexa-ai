package com.nexa.log.application;

import com.nexa.log.domain.repository.LogRepository;
import com.nexa.log.domain.vo.ProfitDashboardEntry;
import com.nexa.log.domain.vo.ProfitDimension;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 利润看板查询用例（应用层，F-6009 GET /api/profit/dashboard）。
 *
 * <p>用例编排：把线上维度字符串归一为受控 {@link ProfitDimension}（未知→400 在领域枚举内拒），
 * 按时间区间调仓储分组聚合售价/成本/利润。聚合口径（GROUP BY 列、cost_missing 判定、利润率派生）
 * 全在领域/基础设施层，本用例只做编排，不含业务规则（backend-engineer §2.1 应用层薄）。</p>
 *
 * <p>领域规则来源：prd-billing F-6009「利润分析看板（按维度聚合）」。鉴权 AdminAuth 由接口层
 * 类级 {@code @RequireRole(ADMIN)} 拦截——成本/利润仅 admin/root 可见（可见性铁律）。</p>
 */
@Service
public class QueryProfitDashboardUseCase {

    private final LogRepository logRepository;

    /** @param logRepository 日志查询仓储（利润聚合数据源为 logs 消费记录） */
    public QueryProfitDashboardUseCase(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * 查询利润看板（F-6009）。
     *
     * @param dimensionWire 维度（{@code model/channel/group}，空缺省 model；未知→400）
     * @param startTimestamp 起始 epoch 秒（可空=不限）
     * @param endTimestamp   结束 epoch 秒（可空=不限）
     * @return 各维度键的利润聚合项（按利润降序）
     */
    public List<ProfitDashboardEntry> query(String dimensionWire, Long startTimestamp, Long endTimestamp) {
        ProfitDimension dimension = ProfitDimension.fromWire(dimensionWire);
        return logRepository.aggregateProfit(dimension, startTimestamp, endTimestamp);
    }
}
