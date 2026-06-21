package com.nexa.shared.security.domain.rbac;

/**
 * 系统角色（RBAC 底座值对象）。
 *
 * <p>对齐 ROLE-PERMISSION-MATRIX §1「repo 底座三级系统角色」与 DB-SCHEMA §1 Role 枚举：
 * {@code common=1 / admin=10 / root=100}，数值大小即权限高低（root&gt;admin&gt;common）。</p>
 *
 * <p>设计取舍：本枚举刻意<b>独立</b>于账号域 {@code com.nexa.account.domain.vo.Role}，使
 * {@code com.nexa.shared.security} 鉴权基础设施<b>不</b>反向依赖账号 bounded context（DDD 上下文解耦）。
 * 两者编码一致，在基础设施边界（JWT 解析处）按 {@code code} 互转。</p>
 *
 * <p>领域规则来源：ROLE-PERMISSION-MATRIX §1 + §6 F-5031「三级系统角色鉴权 common/admin/root」。</p>
 */
public enum ActorRole {

    /** 普通用户（self-scope 资源，common，UserAuth 底线）。 */
    COMMON(1),

    /** 管理员（admin+，AdminAuth 底线，运营∪运维全集）。 */
    ADMIN(10),

    /** 超级管理员（root，RootAuth 专属，全站系统设置）。 */
    ROOT(100);

    private final int code;

    ActorRole(int code) {
        this.code = code;
    }

    /** @return 落库/JWT 承载的整数编码 */
    public int code() {
        return code;
    }

    /**
     * 本角色权限是否 ≥ 给定的最低要求角色（数值大小即权限高低）。
     *
     * <p>三级鉴权护栏的核心比较：AdminAuth 要求 {@code actor.satisfies(ADMIN)}，
     * RootAuth 要求 {@code actor.satisfies(ROOT)}。封装在值对象内，避免散落裸 {@code code >=}。</p>
     *
     * @param required 端点要求的最低角色
     * @return 满足返回 {@code true}
     */
    public boolean satisfies(ActorRole required) {
        return this.code >= required.code;
    }

    /**
     * 本角色权限是否严格高于另一角色（root&gt;admin&gt;common）。
     *
     * <p>用于管理端角色越权护栏（操作者必须严格高于被操作目标），对齐 PRD AC-10。</p>
     *
     * @param other 待比较角色
     * @return 严格高于返回 {@code true}
     */
    public boolean isHigherThan(ActorRole other) {
        return this.code > other.code;
    }

    /**
     * 由整数编码还原角色。
     *
     * @param code 角色编码（1/10/100）
     * @return 对应角色
     * @throws IllegalArgumentException 当编码未知时（防脏数据被静默当作某角色，安全默认）
     */
    public static ActorRole fromCode(int code) {
        for (ActorRole r : values()) {
            if (r.code == code) {
                return r;
            }
        }
        throw new IllegalArgumentException("unknown actor role code: " + code);
    }
}
