package com.nexa.domain.growth.exception;

import com.nexa.sharedkernel.HttpAwareDomainException;

/**
 * 签到配置非法异常（PRD GR-3 校验：{@code Min <= Max} 且 {@code Min/Max >= 0}）。
 *
 * <p>对应 FL-growth GR-3 的「区间非法拒绝态（Min&gt;Max）/ 额度为负拒绝态」。由配置值对象
 * {@code CheckinSetting} 在构造时守护不变量并抛出（充血校验），管理端保存接口拒绝落库。
 * 映射 HTTP 400。</p>
 */
public class InvalidCheckinSettingException extends HttpAwareDomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "INVALID_CHECKIN_SETTING";

    /**
     * @param message 具体非法原因（如「最小额度不能大于最大额度」）
     */
    public InvalidCheckinSettingException(String message) {
        super(CODE, 400, message);
    }
}
