package com.nexa.domain.token.exception;

import com.nexa.sharedkernel.DomainException;

/**
 * 令牌不存在异常（→ 404）。
 *
 * <p>按 id 操作（更新/删除/取明文 key/用量）但令牌缺失（含已软删除）时抛出（F-3004/F-3006/F-3007）。
 * 由接口层翻译为 404。message 仅含 id，不含任何凭证。</p>
 */
public class TokenNotFoundException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "TOKEN_NOT_FOUND";

    /**
     * @param id 缺失的令牌 id
     */
    public TokenNotFoundException(long id) {
        super(CODE, "token not found: id=" + id);
    }
}
