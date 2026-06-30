package com.nexa.interfaces.security.web;

import com.nexa.application.security.port.CurrentActorProvider;
import com.nexa.domain.security.rbac.AuthenticatedActor;
import com.nexa.interfaces.security.annotation.CurrentActor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link CurrentActor} 方法参数解析器（接口层）——把认证主体注入 controller 方法参数。
 *
 * <p>替代各 controller 此前的 {@code X-Operator-Role} 请求头桩：从 {@code SecurityContext} 取出
 * 领域 {@link AuthenticatedActor} 注入到标注 {@link CurrentActor} 的参数上。{@code required=true}
 * （默认）缺主体即抛 {@code AuthenticationRequiredException}（→401）；{@code required=false}
 * 注入 {@code null}（公开端点可选身份）。</p>
 *
 * <p>支持的参数类型：{@link AuthenticatedActor}。控制器因此拿到的是<b>领域聚合根</b>，
 * 不耦合 spring-security 的 Authentication 类型（DDD 接口层只翻译，不漏框架细节）。
 * 取主体经应用层端口 {@link CurrentActorProvider}（实现在基础设施层），接口层不直接依赖
 * spring-security 上下文持有器。</p>
 */
@Component
public class CurrentActorArgumentResolver implements HandlerMethodArgumentResolver {

    private final CurrentActorProvider currentActorProvider;

    /**
     * @param currentActorProvider 当前操作者访问端口（基础设施实现委托安全上下文）
     */
    public CurrentActorArgumentResolver(CurrentActorProvider currentActorProvider) {
        this.currentActorProvider = currentActorProvider;
    }

    /**
     * {@inheritDoc}
     *
     * <p>支持「标注了 {@link CurrentActor} 且类型为 {@link AuthenticatedActor}」的参数。</p>
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentActor.class)
                && AuthenticatedActor.class.isAssignableFrom(parameter.getParameterType());
    }

    /**
     * {@inheritDoc}
     *
     * <p>required=true 时缺主体抛 401；否则可注入 null。</p>
     */
    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        CurrentActor annotation = parameter.getParameterAnnotation(CurrentActor.class);
        boolean required = annotation == null || annotation.required();
        if (required) {
            // 缺主体即认证失败（→401），不返回 null 让 controller 撞 NPE。
            return currentActorProvider.require();
        }
        return currentActorProvider.current().orElse(null);
    }
}
