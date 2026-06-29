package com.nexa.common.security.infrastructure.auth;

import com.nexa.common.security.domain.rbac.AuthenticatedActor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * 已认证操作者的 Spring Security 认证令牌（基础设施层适配）。
 *
 * <p>把领域聚合根 {@link AuthenticatedActor} 适配成 Spring Security 的 {@code Authentication}，
 * 使其能放入 {@code SecurityContextHolder}，复用框架的 {@code .authenticated()} 路由判定与
 * {@code @PreAuthorize}（如启用）能力；同时把领域角色映射为 {@code ROLE_*} 权限，供基于角色的
 * 路径鉴权（AdminAuth/RootAuth 的粗粒度三级判定）使用。</p>
 *
 * <p>DDD 取舍：本类是<b>基础设施适配器</b>（依赖 spring-security），领域聚合根本身仍零框架依赖；
 * 控制器/用例拿到的是领域 {@link AuthenticatedActor}（经 {@code SecurityContextActorHolder} 取出），
 * 不直接耦合本框架类型。</p>
 */
public class ActorAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedActor actor;

    /**
     * 构造一个已认证（authenticated=true）的令牌。
     *
     * <p>权限即角色映射：{@code ROLE_COMMON/ROLE_ADMIN/ROLE_ROOT}（与 {@link com.nexa.common.security.domain.rbac.ActorRole}
     * 同名）。仅在过滤器验签成功后构造，故直接置 authenticated。</p>
     *
     * @param actor 已解析的领域操作者（非空）
     */
    public ActorAuthenticationToken(AuthenticatedActor actor) {
        super(buildAuthorities(actor));
        this.actor = actor;
        setAuthenticated(true);
    }

    private static List<GrantedAuthority> buildAuthorities(AuthenticatedActor actor) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + actor.role().name()));
    }

    /** @return 领域操作者聚合根（供 {@code SecurityContextActorHolder} 取出，控制器/用例使用） */
    public AuthenticatedActor actor() {
        return actor;
    }

    /**
     * {@inheritDoc}
     *
     * <p>无独立凭据对象（凭据是已验签消费掉的 JWT），返回 null。</p>
     */
    @Override
    public Object getCredentials() {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>主体即领域操作者聚合根。</p>
     */
    @Override
    public Object getPrincipal() {
        return actor;
    }

    /**
     * {@inheritDoc}
     *
     * <p>用 userId 作为 name（便于审计日志关联）。</p>
     */
    @Override
    public String getName() {
        return String.valueOf(actor.userId());
    }
}
