package com.nexa.account.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * OAuth state 校验失败异常（CSRF 防护，领域语义）。
 *
 * <p>抛出场景：回调带回的 state 在 StateStore 中不存在 / 已过期 / 已被消费（一次性），
 * 视为可能的 CSRF 攻击或重放，拒绝回调（F-1015/F-1016）。接口层映射 403
 * （对齐 openapi {@code /api/oauth/{provider}} 的 ForbiddenError）。</p>
 *
 * <p>设计依据：backend-engineer §3.2「领域错误用明确错误类型/错误码」。</p>
 */
public class InvalidOAuthStateException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "INVALID_OAUTH_STATE";

    /** 构造默认 state 无效异常（不回显 state 细节，避免给攻击者反馈）。 */
    public InvalidOAuthStateException() {
        super(CODE, "invalid or expired oauth state");
    }
}
