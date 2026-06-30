package com.nexa.application.security.port;

import com.nexa.domain.security.rbac.AuthenticatedActor;

import java.util.Optional;

/**
 * 当前操作者访问端口（应用层，DDD §2.3 接口抽象）。
 *
 * <p>抽象「从当前请求安全上下文取出已认证领域操作者 {@link AuthenticatedActor}」这一动作，
 * 使接口层组件（方法级权限拦截器 / {@code @CurrentActor} 参数解析器）<b>不</b>直接依赖
 * 基础设施的 Spring {@code SecurityContextHolder}。基础设施层提供实现
 * （{@code SecurityContextCurrentActorProvider} 委托 {@code SecurityContextActorHolder}），
 * 单测可注入桩。</p>
 *
 * <p>语义约定（与原 {@code SecurityContextActorHolder} 一致）：
 * <ul>
 *   <li>{@link #current()}：返回 {@link Optional}，未认证（匿名/公开端点）为 empty；</li>
 *   <li>{@link #require()}：受保护端点取操作者，缺失即抛
 *       {@link com.nexa.domain.security.exception.AuthenticationRequiredException}（→401）。</li>
 * </ul>
 * </p>
 */
public interface CurrentActorProvider {

    /**
     * 取当前已认证操作者（不存在则 empty）。
     *
     * @return 当前操作者；匿名/未认证返回 {@link Optional#empty()}
     */
    Optional<AuthenticatedActor> current();

    /**
     * 取当前已认证操作者，缺失即认证失败（受保护端点用）。
     *
     * @return 当前操作者
     * @throws com.nexa.domain.security.exception.AuthenticationRequiredException 上下文无已认证操作者
     */
    AuthenticatedActor require();
}
