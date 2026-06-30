package com.nexa.common.security.web;

import com.nexa.common.security.annotation.CurrentActor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 安全横切的 Spring MVC 装配（接口层）——注册方法级权限拦截器与 {@code @CurrentActor} 解析器。
 *
 * <p>把 RBAC 的接口层组件接入 MVC：
 * <ul>
 *   <li>{@link RequireRoleInterceptor}：执行方法级 {@code @RequireRole} 鉴权级别判定（F-5031）；</li>
 *   <li>{@link CurrentActorArgumentResolver}：把认证主体注入标注 {@link CurrentActor} 的方法参数。</li>
 * </ul>
 * 拦截器对所有路径生效（无标注的 handler 自动放行），不在此白名单化——公开端点本就不标
 * {@code @RequireRole}，且 SecurityConfig 已统一管控放行面，避免两处维护路径清单产生分叉。</p>
 */
@Configuration
public class SecurityWebMvcConfig implements WebMvcConfigurer {

    private final RequireRoleInterceptor requireRoleInterceptor;
    private final CurrentActorArgumentResolver currentActorArgumentResolver;

    /**
     * @param requireRoleInterceptor        方法级权限拦截器
     * @param currentActorArgumentResolver  当前操作者参数解析器
     */
    public SecurityWebMvcConfig(RequireRoleInterceptor requireRoleInterceptor,
                                CurrentActorArgumentResolver currentActorArgumentResolver) {
        this.requireRoleInterceptor = requireRoleInterceptor;
        this.currentActorArgumentResolver = currentActorArgumentResolver;
    }

    /**
     * {@inheritDoc}
     *
     * <p>注册方法级权限拦截器（覆盖全路径，仅对标注 {@code @RequireRole} 的方法实际判定）。</p>
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requireRoleInterceptor).addPathPatterns("/**");
    }

    /**
     * {@inheritDoc}
     *
     * <p>注册 {@code @CurrentActor} 参数解析器。</p>
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentActorArgumentResolver);
    }
}
