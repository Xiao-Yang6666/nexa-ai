package com.nexa.application.ops.port;

import java.util.List;
import java.util.Map;

/**
 * 性能指标查询端口（应用层端口，F-4024 汇总 / F-4025 单模型）。
 *
 * <p>抽象近 N 小时按活动分组/模型聚合的性能指标查询（请求量/延迟/成功率等），由基础设施层
 * 实现（现网查 perf-metrics 时序数据）。本片以 stub 实现承载契约形态，真实数据源接入留待
 * 监控数据落地后替换（端口不变）。仅保留活动分组（filterActiveGroups，API-ENDPOINTS §9.3）。</p>
 */
public interface PerfMetricsQuery {

    /**
     * 近 N 小时各活动分组性能指标汇总（F-4024）。
     *
     * @param hours 时间窗（小时，默认 24，调用方归一）
     * @return 分组名 → 指标快照
     */
    Map<String, MetricSummary> summaryByGroup(int hours);

    /**
     * 单模型近 N 小时各活动分组性能指标（F-4025）。
     *
     * @param model 模型名（必填，调用方已校验非空）
     * @param group 分组过滤（可空，空=全部活动分组）
     * @param hours 时间窗（小时）
     * @return 分组名 → 指标快照（仅活动分组）
     */
    Map<String, MetricSummary> metricsByModel(String model, String group, int hours);

    /**
     * 性能指标快照（只读）。
     *
     * @param requestCount 请求量
     * @param successRate  成功率（0~1）
     * @param avgLatencyMs 平均延迟（毫秒）
     * @param models       该分组涉及的模型（汇总视图用，单模型视图可空）
     */
    record MetricSummary(long requestCount, double successRate, double avgLatencyMs, List<String> models) {
    }
}
