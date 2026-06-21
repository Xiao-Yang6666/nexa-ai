package com.nexa.ops.application.performance;

import com.nexa.ops.application.port.PerfMetricsQuery;
import com.nexa.ops.domain.exception.InvalidMaintenanceRequestException;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 性能指标查询用例（应用层，F-4024 汇总 / F-4025 单模型）。
 *
 * <p>编排：归一时间窗（默认 24 小时、非正归 24）→ 委托指标查询端口。单模型查询强制 model 非空
 * （F-4025「model is required」→400）。仅返回活动分组由端口实现保证（filterActiveGroups）。</p>
 */
@Service
public class GetPerfMetricsUseCase {

    /** 默认时间窗（小时，API-ENDPOINTS §9.3）。 */
    private static final int DEFAULT_HOURS = 24;

    private final PerfMetricsQuery perfMetricsQuery;

    /**
     * @param perfMetricsQuery 性能指标查询端口
     */
    public GetPerfMetricsUseCase(PerfMetricsQuery perfMetricsQuery) {
        this.perfMetricsQuery = perfMetricsQuery;
    }

    /**
     * 各活动分组性能指标汇总（F-4024）。
     *
     * @param hours 时间窗（≤0 归一为默认 24）
     * @return 分组名 → 指标
     */
    public Map<String, PerfMetricsQuery.MetricSummary> summary(int hours) {
        return perfMetricsQuery.summaryByGroup(normalizeHours(hours));
    }

    /**
     * 单模型各活动分组性能指标（F-4025）。
     *
     * @param model 模型名（必填，空→400）
     * @param group 分组过滤（可空）
     * @param hours 时间窗（≤0 归一为默认 24）
     * @return 分组名 → 指标
     * @throws InvalidMaintenanceRequestException model 为空（→400「model is required」）
     */
    public Map<String, PerfMetricsQuery.MetricSummary> byModel(String model, String group, int hours) {
        if (model == null || model.isBlank()) {
            throw new InvalidMaintenanceRequestException("model is required");
        }
        return perfMetricsQuery.metricsByModel(model.trim(), group, normalizeHours(hours));
    }

    /** 时间窗归一：非正值回退默认 24 小时。 */
    private int normalizeHours(int hours) {
        return hours <= 0 ? DEFAULT_HOURS : hours;
    }
}
