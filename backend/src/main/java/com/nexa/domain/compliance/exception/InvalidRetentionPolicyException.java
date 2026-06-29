package com.nexa.domain.compliance.exception;

import com.nexa.common.kernel.DomainException;

/**
 * prompt 留存策略非法异常（F-5017，DC-005）。
 *
 * <p>当配置的 prompt/响应正文留存保留期非法（负数、或超过法务允许的硬上限）时抛出。
 * 领域规则来源：API-ENDPOINTS §14.5 F-5017「正文留存默认关，可开且独立保留期默认 ≤30 天」。
 * 接口层映射为 400。</p>
 */
public final class InvalidRetentionPolicyException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "INVALID_RETENTION_POLICY";

    /**
     * 构造留存策略非法异常。
     *
     * @param message 错误描述（含非法值上下文，便于配置方排错）
     */
    public InvalidRetentionPolicyException(String message) {
        super(CODE, message);
    }
}
