package com.nexa.infrastructure.routing.config;

import com.nexa.domain.routing.repository.AffinityCacheRepository;
import com.nexa.domain.routing.service.AffinityResolver;
import com.nexa.domain.routing.service.CrossGroupRetryScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 路由域（亲和缓存 + 跨组重试）基础设施配置（Spring beans 装配）。
 *
 * <p>领域服务 {@link AffinityResolver}/{@link CrossGroupRetryScheduler} 本身零框架依赖，本类仅提供
 * Spring 容器管理（backend-engineer §2.1 domain 零依赖，infra 提供 DI 装配）。</p>
 */
@Configuration
public class RoutingDomainConfig {

    /**
     * 注册亲和解析服务（依赖仓储注入）。
     *
     * @param cacheRepository 亲和缓存仓储（domain 端口，infra 实现由 Spring 管理）
     * @return AffinityResolver bean
     */
    @Bean
    public AffinityResolver affinityResolver(AffinityCacheRepository cacheRepository) {
        return new AffinityResolver(cacheRepository);
    }

    /**
     * 注册跨组重试调度器（无状态，不需依赖）。
     *
     * @return CrossGroupRetryScheduler bean
     */
    @Bean
    public CrossGroupRetryScheduler crossGroupRetryScheduler() {
        return new CrossGroupRetryScheduler();
    }
}
