package com.nexa.common.security.interfaces.web;

import com.nexa.common.security.domain.rbac.AuthLevel;
import com.nexa.common.security.domain.rbac.AuthenticatedActor;
import com.nexa.common.security.infrastructure.auth.SecurityContextActorHolder;
import com.nexa.common.security.interfaces.annotation.RequireRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 方法级权限拦截器（执行 {@link RequireRole} 声明的鉴权级别判定，F-5031）。
 *
 * <p>在请求进入 controller 方法<b>之前</b>拦截：若目标方法/类标注了 {@link RequireRole}，
 * 取当前认证主体（{@code SecurityContextActorHolder.require()}，缺失即 401），再
 * {@code actor.requireAtLeast(level)} 做级别护栏（不满足 403）。抛出的领域异常由
 * {@code SecurityRbacExceptionHandler} 统一翻译为 HTTP 状态码——拦截器只「判定 + 抛」，不写响应。</p>
 *
 * <p>方法级标注优先于类级（就近覆盖）。无标注的 handler 直接放行（其鉴权由 SecurityConfig 路径级
 * 或公开声明决定），保持与既有最小放行面一致。</p>
 *
 * <p>设计依据：backend-engineer §3.2 错误用明确类型集中翻译；与路径级鉴权互补（见 {@link RequireRole}）。</p>
 */
@Component
public class RequireRoleInterceptor implements HandlerInterceptor {

    /**
     * {@inheritDoc}
     *
     * <p>解析 {@link RequireRole}（方法级优先，回退类级），命中则执行级别护栏。</p>
     *
     * @return {@code true} 放行进入 controller；护栏不通过时抛领域异常（不返回 false）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            // 非 controller 方法（静态资源等）不拦。
            return true;
        }

        RequireRole annotation = resolveAnnotation(handlerMethod);
        if (annotation == null) {
            return true;
        }

        AuthLevel level = annotation.value();
        // require()：受保护端点缺主体即 AuthenticationRequiredException（→401）。
        AuthenticatedActor actor = SecurityContextActorHolder.require();
        // requireAtLeast：级别不足抛 AccessDeniedException（→403，F-5031 越权 403）。
        actor.requireAtLeast(level);
        return true;
    }

    /**
     * 解析生效的 {@link RequireRole}：方法级优先，回退到所属 controller 类级。
     *
     * @param handlerMethod 目标处理方法
     * @return 生效注解；均无返回 null
     */
    private RequireRole resolveAnnotation(HandlerMethod handlerMethod) {
        RequireRole methodLevel = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (methodLevel != null) {
            return methodLevel;
        }
        return handlerMethod.getBeanType().getAnnotation(RequireRole.class);
    }
}
