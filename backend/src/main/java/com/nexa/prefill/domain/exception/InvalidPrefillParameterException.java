package com.nexa.prefill.domain.exception;

/**
 * 预填分组参数非法异常（→ 400，对齐 openapi {@code POST /api/prefill_group} 的
 * 「name/type required」与 {@code GET} 的「type 非法枚举 → 400 invalid type」）。
 *
 * <p>触发场景：name 为空 / type 非 {@code model|tag|endpoint} 枚举 / id 缺失 等客户端可纠正的
 * 入参错误（PRD 模块十五 §14 预填分组 CRUD 入参校验）。领域层构造期即拒，不让脏值落库。</p>
 */
public final class InvalidPrefillParameterException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "PREFILL_INVALID_PARAMETER";

    /**
     * @param message 可读错误描述（不含敏感值）
     */
    public InvalidPrefillParameterException(String message) {
        super(CODE, message);
    }
}
