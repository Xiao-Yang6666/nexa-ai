package com.nexa.domain.growth.exception;

import com.nexa.common.kernel.HttpAwareDomainException;

/**
 * 增长子域用户不存在异常（划转/返利入账等动作的目标用户在 {@code users} 表缺失/已软删除）。
 *
 * <p>防御式：当邀请人/被邀请人在持久化层定位不到（已删或 id 非法）时抛出，避免对幽灵账号入账/划转。
 * 映射 HTTP 404。</p>
 */
public class GrowthUserNotFoundException extends HttpAwareDomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "GROWTH_USER_NOT_FOUND";

    /**
     * @param userId 缺失的用户 id
     */
    public GrowthUserNotFoundException(long userId) {
        super(CODE, 404, "user not found or deleted, id=" + userId);
    }
}
