package com.nexa.token.domain.exception;

/**
 * 令牌 key 无效异常（→ 401）。
 *
 * <p>tokenReadAuth 鉴权路径（F-3012 用量查询）按 Authorization 头携带的令牌 key 反查归属，key 缺失/
 * 格式错/不匹配任何有效（未软删）令牌时抛出。由接口层翻译为 401（对齐 openapi UnauthorizedError）。</p>
 *
 * <p>安全：message 固定且不回显请求中的 key 片段，避免越权探测/枚举。</p>
 */
public class InvalidTokenKeyException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "INVALID_TOKEN_KEY";

    /** 无参构造（不暴露任何 key 片段）。 */
    public InvalidTokenKeyException() {
        super(CODE, "invalid or missing token key");
    }
}
