package com.nexa.infrastructure.security.auth;

import com.nexa.domain.security.exception.AuthenticationRequiredException;
import com.nexa.domain.security.rbac.AuthenticatedActor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 当前操作者上下文访问器（基础设施层适配工具）。
 *
 * <p>统一封装「从 Spring {@code SecurityContextHolder} 取出领域 {@link AuthenticatedActor}」，
 * 让控制器 / 方法级权限拦截器 / {@code @CurrentActor} 解析器都走同一入口，不在多处散落对
 * {@code SecurityContextHolder} 与 {@link ActorAuthenticationToken} 的直接读取。</p>
 *
 * <p>用法语义：
 * <ul>
 *   <li>{@link #current()}：返回 {@link Optional}，未认证（匿名/公开端点）为 empty；</li>
 *   <li>{@link #require()}：受保护端点取操作者，缺失即抛
 *       {@link AuthenticationRequiredException}（→401）——把「忘了鉴权」变成显式失败，而非 NPE。</li>
 * </ul>
 * </p>
 */
public final class SecurityContextActorHolder {

    private SecurityContextActorHolder() {
        // 工具类，禁止实例化。
    }

    /**
     * 取当前已认证操作者（不存在则 empty）。
     *
     * @return 当前操作者；匿名/未认证返回 {@link Optional#empty()}
     */
    public static Optional<AuthenticatedActor> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof ActorAuthenticationToken token && token.isAuthenticated()) {
            return Optional.of(token.actor());
        }
        return Optional.empty();
    }

    /**
     * 取当前已认证操作者，缺失即认证失败（受保护端点用）。
     *
     * @return 当前操作者
     * @throws AuthenticationRequiredException 上下文无已认证操作者
     */
    public static AuthenticatedActor require() {
        return current().orElseThrow(AuthenticationRequiredException::new);
    }
}
