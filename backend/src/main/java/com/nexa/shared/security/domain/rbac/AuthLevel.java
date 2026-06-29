package com.nexa.shared.security.domain.rbac;

/**
 * 鉴权级别（三级过滤器要求的最低身份门槛，值对象）。
 *
 * <p>对齐 openapi.yaml {@code securitySchemes} 与 ROLE-PERMISSION-MATRIX §3：
 * <ul>
 *   <li>{@link #USER}  —— UserAuth：要求已认证且 Role≥common（self-scope 端点）；</li>
 *   <li>{@link #ADMIN} —— AdminAuth：要求 Role≥admin（用户/渠道/计费/部署管理）；</li>
 *   <li>{@link #ROOT}  —— RootAuth：要求 Role==root（全站系统设置/OAuth provider 配置）。</li>
 * </ul>
 * 每级映射到一个「最低要求角色」{@link ActorRole}，由 {@link AuthenticatedActor#requireAtLeast} 守护。</p>
 *
 * <p>领域规则来源：ROLE-PERMISSION-MATRIX §6 F-5031「AdminAuth/RootAuth 中间件落地」。</p>
 */
public enum AuthLevel {

    /** UserAuth：最低 common。 */
    USER(ActorRole.COMMON),

    /** AdminAuth：最低 admin。 */
    ADMIN(ActorRole.ADMIN),

    /** RootAuth：要求 root。 */
    ROOT(ActorRole.ROOT);

    private final ActorRole minimumRole;

    AuthLevel(ActorRole minimumRole) {
        this.minimumRole = minimumRole;
    }

    /** @return 满足本鉴权级别所需的最低角色 */
    public ActorRole minimumRole() {
        return minimumRole;
    }

    /**
     * 给定角色是否满足本鉴权级别。
     *
     * @param role 操作者角色
     * @return 满足返回 {@code true}
     */
    public boolean isSatisfiedBy(ActorRole role) {
        return role.satisfies(minimumRole);
    }
}
