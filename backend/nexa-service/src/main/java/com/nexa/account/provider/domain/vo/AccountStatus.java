package com.nexa.account.provider.domain.vo;

import com.nexa.account.provider.domain.exception.InvalidAccountParameterException;

/**
 * 供应商账号状态值对象（枚举，对齐 sub2api accounts.status varchar(20) default 'active'）。
 *
 * <ul>
 *   <li>{@link #ACTIVE} —— 启用，可参与调度（未过期/未限流时）。</li>
 *   <li>{@link #DISABLED} —— 手动禁用，不参与调度。</li>
 *   <li>{@link #RATE_LIMITED} —— 上游限流中，待恢复时间到达后可恢复 ACTIVE。</li>
 * </ul>
 *
 * <p>状态以字符串码持久化（对齐参考表 varchar，区别于 channel 的整数码），
 * 通过 {@link #fromCode(String)} 解析、{@link #code()} 落库。</p>
 */
public enum AccountStatus {

    /** 启用（默认）。 */
    ACTIVE("active"),

    /** 手动禁用。 */
    DISABLED("disabled"),

    /** 限流中（可被恢复机制重新启用）。 */
    RATE_LIMITED("rate_limited");

    private final String code;

    AccountStatus(String code) {
        this.code = code;
    }

    /** @return 持久化字符串码 */
    public String code() {
        return code;
    }

    /**
     * 由字符串码解析状态（未知码视为非法入参，不静默兜底）。
     *
     * @param code 状态字符串码
     * @return 对应状态
     * @throws InvalidAccountParameterException 未知/空状态码
     */
    public static AccountStatus fromCode(String code) {
        if (code != null) {
            for (AccountStatus s : values()) {
                if (s.code.equals(code)) {
                    return s;
                }
            }
        }
        throw new InvalidAccountParameterException("unknown account status code: " + code);
    }

    /** @return 是否为启用态 */
    public boolean isActive() {
        return this == ACTIVE;
    }
}
