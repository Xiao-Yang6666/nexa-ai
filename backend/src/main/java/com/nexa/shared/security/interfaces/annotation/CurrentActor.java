package com.nexa.shared.security.interfaces.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法参数注解：注入当前认证操作者（{@link com.nexa.shared.security.domain.rbac.AuthenticatedActor}）。
 *
 * <p>替代此前各 controller 临时用的 {@code X-Operator-Role} 请求头桩。标注在 controller 方法参数上，
 * 由 {@code CurrentActorArgumentResolver} 从 {@code SecurityContext} 取出领域操作者注入。</p>
 *
 * <p>语义：默认 {@code required=true}——受保护端点参数缺操作者即抛
 * {@code AuthenticationRequiredException}（→401），把「漏鉴权」变成显式失败而非 null 主体。</p>
 *
 * <p>用法：
 * <pre>{@code
 *   @PostMapping("/manage")
 *   public ApiResponse<Void> manage(@Valid @RequestBody Req r, @CurrentActor AuthenticatedActor operator) { ... }
 * }</pre>
 * </p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentActor {

    /**
     * 是否必须存在认证主体。
     *
     * @return {@code true}（默认）缺失即 401；{@code false} 允许注入 {@code null}（公开端点可选身份）
     */
    boolean required() default true;
}
