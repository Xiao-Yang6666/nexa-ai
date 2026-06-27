package com.nexa.growth.domain.exception;

import com.nexa.shared.kernel.HttpAwareDomainException;

/**
 * 今日已签到异常（PRD GR-1 分支「同日重复签到被 {@code idx_user_checkin_date} 唯一索引+事务拦截」）。
 *
 * <p>对应 FL-growth GR-1 的「今日已签到态 / 并发重复拦截态」。映射 HTTP 400
 * （openapi {@code /api/user/checkin} 400「今日已签」，F-1048）。应用层在「已存在当日记录」或
 * 「持久化命中唯一索引冲突」两条路径上统一抛出本异常，给前端稳定「今日已签到」提示。</p>
 */
public class AlreadyCheckedInException extends HttpAwareDomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "ALREADY_CHECKED_IN";

    /** 构造今日已签到异常（稳定用户可见提示「今日已签到」）。 */
    public AlreadyCheckedInException() {
        super(CODE, 400, "今日已签到");
    }
}
