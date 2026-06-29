package com.nexa.common.security.domain.rbac;

import com.nexa.common.security.domain.exception.AccessDeniedException;

import java.util.Objects;

/**
 * 认证主体（操作者身份，充血聚合根）。
 *
 * <p>三级鉴权过滤器解析令牌/会话后构造本对象，注入 SecurityContext，全程承载「当前操作者是谁、
 * 什么角色」。鉴权护栏（级别判定、self-scope 越权判定、角色层级判定）作为<b>行为方法</b>挂在
 * 本聚合根上（充血模型，backend-engineer §2.2），用例/控制器只调 {@code actor.requireXxx()}，
 * 不在外部散落 {@code if (role >= ...)} 裸比较。</p>
 *
 * <p>不变量：{@code userId} 必为正、{@code role} 非空。一旦构造即代表「一个已认证的合法操作者」，
 * 未认证场景由过滤器抛 {@code AuthenticationRequiredException}，<b>不会</b>产生本对象（不存在
 * 「匿名 Actor」表示访客）。</p>
 *
 * <p>领域规则来源：ROLE-PERMISSION-MATRIX §3「self-scope 按 user_id 强制过滤，越权他人资源 403」
 * + §6 F-5031/F-5032。零框架依赖，可纯 JUnit 单测。</p>
 */
public final class AuthenticatedActor {

    private final long userId;
    private final String username;
    private final ActorRole role;

    /**
     * @param userId   操作者用户 id（必 &gt; 0）
     * @param username 用户名（可空，仅用于日志/审计可读性，不参与鉴权判定）
     * @param role     操作者系统角色（非空）
     * @throws IllegalArgumentException userId 非正
     * @throws NullPointerException     role 为空
     */
    public AuthenticatedActor(long userId, String username, ActorRole role) {
        if (userId <= 0) {
            // 不吞错：非法主体 id 是令牌伪造/解析 bug 的信号，构造期即拒，不让脏身份流入鉴权。
            throw new IllegalArgumentException("authenticated actor userId must be positive, got " + userId);
        }
        this.userId = userId;
        this.username = username;
        this.role = Objects.requireNonNull(role, "actor role must not be null");
    }

    /** @return 操作者用户 id */
    public long userId() {
        return userId;
    }

    /** @return 用户名（可空，非鉴权依据） */
    public String username() {
        return username;
    }

    /** @return 操作者系统角色 */
    public ActorRole role() {
        return role;
    }

    /**
     * 是否满足给定鉴权级别（不抛异常的查询版）。
     *
     * @param level 端点要求的鉴权级别
     * @return 满足返回 {@code true}
     */
    public boolean satisfies(AuthLevel level) {
        return level.isSatisfiedBy(role);
    }

    /**
     * 断言满足给定鉴权级别，否则越权拒绝（命令版护栏）。
     *
     * <p>三级过滤器与方法级权限注解的统一入口：AdminAuth 调 {@code requireAtLeast(ADMIN)}，
     * RootAuth 调 {@code requireAtLeast(ROOT)}。不满足抛 {@link AccessDeniedException}（→403，
     * 对齐 F-5031 越权路由 403）。</p>
     *
     * @param level 端点要求的鉴权级别
     * @throws AccessDeniedException 角色不满足该级别
     */
    public void requireAtLeast(AuthLevel level) {
        if (!satisfies(level)) {
            // 不回显具体角色/级别细节，避免给攻击者权限探测反馈（安全默认）。
            throw new AccessDeniedException("access denied: requires " + level.name().toLowerCase() + " privilege");
        }
    }

    /**
     * 断言对目标资源拥有 self-scope 访问权（命令版护栏，F-5032）。
     *
     * <p>规则：操作者只能命中<b>本人</b>资源（令牌/额度/任务/日志），除非其为 admin+
     * （管理端有全量权限，矩阵 O09/O10 admin+ ✅全量）。即「目标 userId == 自己」或「自己是 admin+」
     * 二者满足其一即放行，否则越权 403。</p>
     *
     * @param targetUserId 被访问资源所属的用户 id
     * @throws AccessDeniedException 既非本人资源、又非 admin+
     */
    public void requireSelfScopeOrAdmin(long targetUserId) {
        boolean ownResource = this.userId == targetUserId;
        boolean adminOverride = role.satisfies(ActorRole.ADMIN);
        if (!ownResource && !adminOverride) {
            // 不回显目标归属（避免账号枚举），统一稳定提示。
            throw new AccessDeniedException("access denied: cross-user resource access forbidden");
        }
    }

    /**
     * 断言本操作者可对「目标角色」执行管理操作（角色层级护栏）。
     *
     * <p>规则（PRD AC-10）：操作者必须<b>严格高于</b>目标角色才能管理（创建/封禁/升降级），
     * 不可操作 ≥ 自身角色的用户。本方法是管理端用户管理（O09）越权护栏的领域入口。</p>
     *
     * @param targetRole 被操作目标用户的角色
     * @throws AccessDeniedException 操作者未严格高于目标角色
     */
    public void requireHigherThan(ActorRole targetRole) {
        if (!role.isHigherThan(targetRole)) {
            throw new AccessDeniedException("access denied: cannot operate on a role equal or higher than your own");
        }
    }
}
