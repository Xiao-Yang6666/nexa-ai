package com.nexa.domain.account.vo;

/**
 * 用户角色枚举（值对象）。
 *
 * <p>编码对齐 openapi.yaml UserVO.role 描述与 DB-SCHEMA §1 Role 枚举：
 * {@code 1=common, 10=admin, 100=root}。数值大小即权限高低，护栏：不可操作
 * 角色 ≥ 自身角色（本切片不涉及管理操作，仅承载注册默认角色与登录回显）。</p>
 *
 * <p>领域规则注释来源：API-ENDPOINTS §1.1 + DB-SCHEMA §1「Role common=普通(默认)/admin/root」。</p>
 */
public enum Role {

    /** 普通用户（注册默认角色，PRD AC-1 R13「创建 common 用户」）。 */
    COMMON(1),

    /** 管理员。 */
    ADMIN(10),

    /** 超级管理员。 */
    ROOT(100);

    private final int code;

    Role(int code) {
        this.code = code;
    }

    /** @return 落库/下发的整数编码 */
    public int code() {
        return code;
    }

    /**
     * 是否权限高于另一角色（数值大小即权限高低，root&gt;admin&gt;common）。
     *
     * <p>用于角色越权护栏的核心比较（PRD AC-10 M3/M7）：操作者必须 {@code 自身角色 > 目标角色}
     * 才允许操作。封装在值对象内，避免在用例/控制器里散落 {@code code() >} 的裸比较。</p>
     *
     * @param other 待比较角色
     * @return 本角色权限严格高于 {@code other} 返回 {@code true}
     */
    public boolean isHigherThan(Role other) {
        return this.code > other.code;
    }

    /**
     * 是否权限不低于另一角色（同级或更高）。
     *
     * <p>护栏拒绝条件即「目标角色 ≥ 操作者角色」，等价于 {@code target.isAtLeast(operator)}。</p>
     *
     * @param other 待比较角色
     * @return 本角色权限 ≥ {@code other} 返回 {@code true}
     */
    public boolean isAtLeast(Role other) {
        return this.code >= other.code;
    }

    /**
     * 由整数编码还原角色。
     *
     * @param code 角色编码
     * @return 对应角色
     * @throws IllegalArgumentException 当编码未知时（防止脏数据被静默当作某角色）
     */
    public static Role fromCode(int code) {
        for (Role r : values()) {
            if (r.code == code) {
                return r;
            }
        }
        throw new IllegalArgumentException("unknown role code: " + code);
    }
}
