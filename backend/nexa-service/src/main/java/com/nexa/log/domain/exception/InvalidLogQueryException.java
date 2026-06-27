package com.nexa.log.domain.exception;

import com.nexa.shared.kernel.HttpAwareDomainException;

/**
 * 日志查询入参非法（→400）。
 *
 * <p>承载契约定义的所有 400 分支：
 * <ul>
 *   <li>F-4006 清理历史日志 {@code target_timestamp==0} →「target timestamp is required」；</li>
 *   <li>F-4009 用户自助配额按日数据时间跨度超 1 月（{@code end-start>2592000s}）→「时间跨度不能超过 1 个月」；</li>
 *   <li>F-4003 按令牌 key 查日志 {@code token_id==0} →「无效的令牌」；</li>
 *   <li>F-4010 排行榜 {@code period} 非法 →「invalid period」。</li>
 * </ul>
 * message 由领域规则直接给出（与现网文案一致），接口层只翻译状态码不改文案。</p>
 */
public class InvalidLogQueryException extends HttpAwareDomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "LOG_INVALID_PARAMETER";

    /**
     * @param message 可读错误描述（采用与现网一致的稳定文案）
     */
    public InvalidLogQueryException(String message) {
        super(CODE, 400, message);
    }
}
