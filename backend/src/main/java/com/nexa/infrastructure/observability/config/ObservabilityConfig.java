package com.nexa.infrastructure.observability.config;

import com.nexa.domain.observability.alert.AlertRouter;
import com.nexa.domain.observability.alert.SloEvaluator;
import com.nexa.domain.observability.metrics.MetricRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 可观测域基础设施配置（Spring beans 装配，F-5010~F-5012）。
 *
 * <p>领域聚合根/服务（{@link MetricRegistry} 指标注册表、{@link SloEvaluator} SLO 检测、{@link AlertRouter}
 * 告警路由）本身零框架依赖（仅用 JDK 原语），本类仅提供 Spring 容器管理（backend-engineer §2.1 domain 零依赖，
 * infra 提供 DI 装配）。{@link MetricRegistry} 注册为<b>单例</b>——全应用共享一份指标累计状态，relay 链路埋点与
 * {@code /metrics} 导出读写同一实例。</p>
 */
@Configuration
public class ObservabilityConfig {

    /**
     * 注册 RED 指标注册表（应用级单例，承载全量请求/错误/延迟/额度累计）。
     *
     * @return MetricRegistry 单例 bean
     */
    @Bean
    public MetricRegistry metricRegistry() {
        return new MetricRegistry();
    }

    /**
     * 注册 SLO 越界检测领域服务（无状态）。
     *
     * @return SloEvaluator bean
     */
    @Bean
    public SloEvaluator sloEvaluator() {
        return new SloEvaluator();
    }

    /**
     * 注册告警渠道路由领域服务（无状态）。
     *
     * @return AlertRouter bean
     */
    @Bean
    public AlertRouter alertRouter() {
        return new AlertRouter();
    }
}
