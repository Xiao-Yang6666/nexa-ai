package com.nexa.routing.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 亲和缓存运维入参非法异常（F-2032 清空 / F-2033 用量统计入参校验）。
 *
 * <p>领域规则来源：PRD CH-4 / API-ENDPOINTS 5.4。如清空缓存时 all 与 rule_name 二选一未满足、
 * 用量统计 rule_name/key_fp 必填缺失等，接口层翻译为 400。</p>
 */
public class InvalidAffinityParameterException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "INVALID_AFFINITY_PARAMETER";

    /**
     * @param message 错误描述（不含敏感凭证）
     */
    public InvalidAffinityParameterException(String message) {
        super(CODE, message);
    }
}
