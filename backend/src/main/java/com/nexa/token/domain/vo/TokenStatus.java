package com.nexa.token.domain.vo;

import com.nexa.token.domain.exception.InvalidTokenParameterException;

/**
 * 令牌状态值对象（不可变枚举，对齐 DB-SCHEMA §2「Status 1=启用 / 禁用」）。
 *
 * <p>领域规则来源：DB-SCHEMA §2「枚举：Status `1=启用` / 禁用；派生态：已过期、额度耗尽、已删除」。
 * 本枚举只承载持久化的 status 整数态（启用/禁用）；已过期/额度耗尽/已删除为<b>派生态</b>，由聚合根
 * 依 expired_time/quota/deleted_at 运行期计算，不落 status 列（F-3006 status_only 仅在启用/禁用间切换）。</p>
 */
public enum TokenStatus {

    /** 启用（status=1）。 */
    ENABLED(1),

    /** 禁用（status=2，用户手动禁用）。 */
    DISABLED(2);

    private final int code;

    TokenStatus(int code) {
        this.code = code;
    }

    /** @return 持久化整数码 */
    public int code() {
        return code;
    }

    /**
     * 由持久化整数码还原状态。
     *
     * <p>非启用/禁用的历史/异常码（如现网曾用的已过期=3/耗尽=4）一律归并为 {@link #DISABLED}——
     * 派生态不再落 status（见类注释），重建时把非启用统一视作禁用，避免脏码穿透领域层。</p>
     *
     * @param code 持久化整数码
     * @return 状态值对象
     */
    public static TokenStatus fromCode(int code) {
        return code == ENABLED.code ? ENABLED : DISABLED;
    }

    /**
     * 由客户端入参（status_only 更新）解析目标状态，仅允许启用(1)/禁用(2)。
     *
     * @param code 客户端提交的目标状态码
     * @return 状态值对象
     * @throws InvalidTokenParameterException 非 1/2 的非法目标状态
     */
    public static TokenStatus requireValid(Integer code) {
        if (code == null) {
            throw new InvalidTokenParameterException("token status must not be null");
        }
        if (code == ENABLED.code) {
            return ENABLED;
        }
        if (code == DISABLED.code) {
            return DISABLED;
        }
        throw new InvalidTokenParameterException("token status must be 1(enabled) or 2(disabled), got " + code);
    }
}
