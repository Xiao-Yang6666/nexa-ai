package com.nexa.token.domain.exception;

/**
 * 令牌越权访问异常（→ 403）。
 *
 * <p>self-scope 铁律违反：令牌为用户私有资源，按 user_id 强制过滤；当前操作者操作非本人令牌
 * （读/改/删/取明文 key）时抛出（ROLE-PERMISSION-MATRIX §3「越权他人资源 403」，
 * F-3004/F-3005/F-3006/F-3007）。由接口层翻译为 403。</p>
 *
 * <p>安全：message 不回显令牌明文 key，也不暴露目标令牌归属用户，避免越权探测信息泄露。</p>
 */
public class TokenAccessDeniedException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "TOKEN_ACCESS_DENIED";

    /**
     * @param id 被越权访问的令牌 id
     */
    public TokenAccessDeniedException(long id) {
        super(CODE, "access denied for token: id=" + id);
    }
}
