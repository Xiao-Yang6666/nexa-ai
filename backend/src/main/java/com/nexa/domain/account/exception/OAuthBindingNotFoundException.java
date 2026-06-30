package com.nexa.domain.account.exception;

import com.nexa.sharedkernel.DomainException;

/**
 * OAuth 绑定不存在异常（解绑时按定位条件未命中绑定）。
 *
 * <p>抛出场景：本人解绑（F-1026）或管理端解绑（F-1027）按 {@code (userId, providerRefId)} 定位待解绑的
 * 自定义 provider 绑定时未命中——该用户在该自定义 provider 下没有绑定，或 {@code provider_id} 不存在。
 * 接口层翻译为 404（对齐 openapi NotFoundError 语义）。</p>
 *
 * <p>领域规则来源：openapi {@code DELETE /api/user/self/oauth/bindings/{provider_id}}（F-1026）/
 * {@code DELETE /api/user/{id}/oauth/bindings/{provider_id}}（F-1027）——解绑前置条件「绑定存在」。
 * 设计依据：backend-engineer §3.2「领域错误用明确错误类型/错误码」。</p>
 */
public class OAuthBindingNotFoundException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "OAUTH_BINDING_NOT_FOUND";

    /**
     * @param userId        归属用户 id
     * @param providerRefId 自定义 provider 整数主键（解绑端点 {@code {provider_id}}）
     */
    public OAuthBindingNotFoundException(long userId, long providerRefId) {
        super(CODE, "oauth binding not found: userId=" + userId + ", providerId=" + providerRefId);
    }
}
