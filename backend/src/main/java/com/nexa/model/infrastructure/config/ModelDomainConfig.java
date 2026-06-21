package com.nexa.model.infrastructure.config;

import com.nexa.model.domain.service.ModelSyncPlanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型/供应商元数据域基础设施配置（Spring beans 装配）。
 *
 * <p>领域服务 {@link ModelSyncPlanner} 本身零框架依赖（纯领域逻辑，可单测），本类仅提供 Spring 容器
 * 管理（backend-engineer §2.1 domain 零依赖，infra 提供 DI 装配；与 RoutingDomainConfig 同构）。</p>
 */
@Configuration
public class ModelDomainConfig {

    /**
     * 注册模型同步计划领域服务（无状态，无依赖）。
     *
     * @return ModelSyncPlanner bean
     */
    @Bean
    public ModelSyncPlanner modelSyncPlanner() {
        return new ModelSyncPlanner();
    }
}
