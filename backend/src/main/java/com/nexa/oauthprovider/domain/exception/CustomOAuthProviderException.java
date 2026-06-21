package com.nexa.oauthprovider.domain.exception;

/**
 * 自定义 OAuth provider 领域异常基类。
 *
 * <p>oauthprovider 限界上下文（bounded context）的业务规则违反统一继承本类，携带稳定业务错误码，
 * 供接口层翻译为 HTTP 状态码 + 响应 message。与 {@code com.nexa.account} 的 DomainException 平行——
 * 各 BC 自持领域异常基类，避免跨上下文耦合（backend-engineer §2.1 + §3.2）。</p>
 */
public abstract class CustomOAuthProviderException extends RuntimeException {

    private final String code;

    /**
     * @param code    稳定业务错误码（如 {@code CUSTOM_OAUTH_PROVIDER_NOT_FOUND}）
     * @param message 面向开发者/可读的错误描述
     */
    protected CustomOAuthProviderException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** @return 稳定业务错误码 */
    public String code() {
        return code;
    }
}
