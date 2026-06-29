package com.nexa.application.observability;

import com.nexa.domain.observability.metrics.MetricRegistry;
import com.nexa.domain.observability.metrics.MetricSnapshot;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 指标导出用例（应用层，F-5010）。
 *
 * <p>薄壳编排：从 RED 指标注册表（聚合根）取导出快照，交接口层渲染为 Prometheus 文本。导出文本格式渲染
 * 属基础设施细节（在 {@code infrastructure} 的渲染器），本用例只产出中性的领域快照列表，符合
 * backend-engineer §2.1「application 薄、不含领域规则/格式细节」。</p>
 *
 * <p>领域规则来源：NFR-O01/O02「{@code /metrics} 抓取端点暴露按渠道/模型的请求率/错误率/延迟与额度速率」。</p>
 */
@Service
public class ExportMetricsUseCase {

    private final MetricRegistry registry;

    /**
     * @param registry RED 指标注册表（聚合根，由 infra 配置为单例 bean）
     */
    public ExportMetricsUseCase(MetricRegistry registry) {
        this.registry = registry;
    }

    /**
     * 取当前全部指标序列快照。
     *
     * @return 不可变快照列表
     */
    public List<MetricSnapshot> currentSnapshots() {
        return registry.snapshot();
    }
}
