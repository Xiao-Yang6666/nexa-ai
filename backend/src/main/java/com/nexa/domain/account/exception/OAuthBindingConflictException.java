package com.nexa.domain.account.exception;

import com.nexa.common.kernel.DomainException;

/**
 * OAuth 绑定冲突异常（领域语义）。
 *
 * <p>抛出场景：某第三方账号（provider + providerUserId）已绑定到<b>另一个</b>本站用户，
 * 又被请求绑定到当前登录用户时（违反「每 provider 一账号唯一」，DB-SCHEMA §13
 * 复合唯一 {@code ux_provider_userid}）。接口层映射 400（对齐 openapi BadRequestError）。</p>
 *
 * <p>设计依据：backend-engineer §3.2「领域错误用明确错误类型/错误码」。</p>
 */
public class OAuthBindingConflictException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "OAUTH_BINDING_CONFLICT";

    /**
     * @param provider provider 标识串（不回显对端 userId 等敏感细节）
     */
    public OAuthBindingConflictException(String provider) {
        super(CODE, "third-party account is already bound to another user for provider: " + provider);
    }
}
