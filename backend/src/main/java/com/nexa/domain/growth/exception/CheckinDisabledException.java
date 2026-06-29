package com.nexa.domain.growth.exception;

import com.nexa.shared.kernel.HttpAwareDomainException;

/**
 * 签到功能未启用异常（PRD GR-1/GR-2 前置条件「{@code CheckinSetting.Enabled=false} 时接口直接返回未启用」）。
 *
 * <p>对应 FL-growth GR-1 状态机的「未启用」终态、GR-2 的「未启用空壳态」。映射 HTTP 400
 * （openapi {@code /api/user/checkin} 400「签到未启用」）。</p>
 */
public class CheckinDisabledException extends HttpAwareDomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "CHECKIN_DISABLED";

    /** 构造未启用异常（稳定用户可见提示「签到功能未启用」）。 */
    public CheckinDisabledException() {
        super(CODE, 400, "签到功能未启用");
    }
}
