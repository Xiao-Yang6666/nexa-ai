package com.nexa.domain.account.vo;

/**
 * 用户账号状态（值对象）。
 *
 * <p>编码对齐 DB-SCHEMA §1「Status 1=启用，≠1=禁用」与 openapi UserView.status。
 * 登录闸门用 {@link #isEnabled()} 判断（PRD AC-2 L3「校验 Status=UserStatusEnabled」）。</p>
 */
public enum UserStatus {

    /** 启用（编码 1，注册默认）。 */
    ENABLED(1),

    /** 禁用/封禁（编码 2，泛指一切 ≠1）。 */
    DISABLED(2);

    private final int code;

    UserStatus(int code) {
        this.code = code;
    }

    /** @return 落库/下发的整数编码 */
    public int code() {
        return code;
    }

    /** @return 是否为启用态（仅编码 1 为启用，对齐「≠1=禁用」语义） */
    public boolean isEnabled() {
        return this == ENABLED;
    }

    /**
     * 由整数编码还原状态。
     *
     * <p>领域规则：DB-SCHEMA §1「Status ≠1 一律视为禁用」，因此任何非 1 编码统一映射为
     * {@link #DISABLED}，不抛异常（兼容历史可能存在的多种禁用码）。</p>
     *
     * @param code 状态编码
     * @return 启用（1）或禁用（其它）
     */
    public static UserStatus fromCode(int code) {
        return code == ENABLED.code ? ENABLED : DISABLED;
    }
}
