package com.nexa.infrastructure.security.auth;

import com.nexa.application.security.port.CurrentActorProvider;
import com.nexa.domain.security.rbac.AuthenticatedActor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link CurrentActorProvider} 的基础设施实现——委托静态 {@link SecurityContextActorHolder}。
 *
 * <p>把「从 Spring {@code SecurityContextHolder} 取领域操作者」这一基础设施动作以 bean 形式暴露，
 * 让接口层（拦截器/参数解析器）通过应用层端口 {@link CurrentActorProvider} 依赖它，而不直接
 * 静态调用本层工具——依赖方向变为 {@code interfaces → application（端口）}，由本实现在
 * {@code infrastructure} 完成与 spring-security 的适配，符合 DDD 依赖倒置。</p>
 *
 * <p>静态 {@link SecurityContextActorHolder} 保留：基础设施内部（如自定义 Authentication）仍直接使用，
 * 方向合法（infra→infra）；本类只是为跨层调用方提供可注入入口。</p>
 */
@Component
public class SecurityContextCurrentActorProvider implements CurrentActorProvider {

    @Override
    public Optional<AuthenticatedActor> current() {
        return SecurityContextActorHolder.current();
    }

    @Override
    public AuthenticatedActor require() {
        return SecurityContextActorHolder.require();
    }
}
