package com.nexa.infrastructure.ops.monitoring;

import com.nexa.application.ops.port.PerfMetricsQuery;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link PerfMetricsQuery} 的空数据 stub 实现（基础设施层，F-4024/F-4025）。
 *
 * <p>性能指标的真实数据源（perf-metrics 时序统计）由 relay 转发链路在 W3 落地后逐步累积，
 * 本 W5 切片先提供承载契约形态的空实现：返回空分组映射（合法的「暂无数据」状态），端口契约
 * 不变，待真实数据源接入只替换本实现（DDD §2.3 依赖倒置的价值）。</p>
 *
 * <p>这样做的理由：性能监控端点需要先有可调用的 bean 让控制器装配与契约联调通过；用空 stub
 * 而非抛 NotImplemented，避免公开/登录可见的 pricing 性能页因后端未就绪而 500（API-ENDPOINTS
 * §9.3 这两个端点是 PublicOrUserAuth，体验上应优雅降级为空数据）。</p>
 */
@Component
public class StubPerfMetricsQuery implements PerfMetricsQuery {

    /** {@inheritDoc} */
    @Override
    public Map<String, MetricSummary> summaryByGroup(int hours) {
        // 暂无时序数据源：返回空映射（前端渲染为「暂无数据」，不报错）。
        return new LinkedHashMap<>();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, MetricSummary> metricsByModel(String model, String group, int hours) {
        return new LinkedHashMap<>();
    }

    /** 占位：单个空指标快照（保留供未来接入时参考形态）。 */
    @SuppressWarnings("unused")
    private MetricSummary emptyMetric() {
        return new MetricSummary(0, 0.0, 0.0, List.of());
    }
}
