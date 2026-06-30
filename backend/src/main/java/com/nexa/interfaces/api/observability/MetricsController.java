package com.nexa.interfaces.api.observability;

import com.nexa.application.observability.ExportMetricsUseCase;
import com.nexa.infrastructure.observability.metrics.PrometheusTextRenderer;
import com.nexa.domain.security.rbac.AuthLevel;
import com.nexa.interfaces.security.annotation.RequireRole;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prometheus 指标导出控制器（接口层，F-5010）。
 *
 * <p>承载 {@code GET /metrics} 抓取端点（对齐 openapi ops 模块）：RootAuth（运维/Root），返回
 * Prometheus 文本格式的 RED 指标（按渠道/模型维度的请求率/错误率/延迟与额度速率，NFR-O01/O02）。
 * 接口层只做「翻译」（backend-engineer §2.1）：调用例取快照、调渲染器出文本、设正确 Content-Type。</p>
 *
 * <p>鉴权说明：openapi 标 {@code rootAuth}，故方法级 {@code @RequireRole(ROOT)}——指标含全量渠道/模型
 * 运营维度，仅运维/Root 可抓取（与契约一致，不放宽）。</p>
 */
@RestController
public class MetricsController {

    private final ExportMetricsUseCase exportMetricsUseCase;
    private final PrometheusTextRenderer renderer;

    /**
     * @param exportMetricsUseCase 指标导出用例
     * @param renderer             Prometheus 文本渲染器
     */
    public MetricsController(ExportMetricsUseCase exportMetricsUseCase, PrometheusTextRenderer renderer) {
        this.exportMetricsUseCase = exportMetricsUseCase;
        this.renderer = renderer;
    }

    /**
     * Prometheus RED 指标导出（F-5010，RootAuth）。
     *
     * @return Prometheus 文本格式指标（text/plain; version=0.0.4）
     */
    @RequireRole(AuthLevel.ROOT)
    @GetMapping("/metrics")
    public ResponseEntity<String> metrics() {
        String text = renderer.render(exportMetricsUseCase.currentSnapshots());
        return ResponseEntity.ok()
                .header("Content-Type", PrometheusTextRenderer.CONTENT_TYPE)
                .body(text);
    }
}
