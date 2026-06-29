package com.nexa.domain.oauthprovider.exception;

/**
 * 自定义 OAuth provider 配置非法异常（领域语义）。
 *
 * <p>抛出场景：创建/更新 provider 时字段不合法（name 空、client_id/secret 空、端点 URL 非法/超长、
 * issuer 非法等）。接口层映射 400（对齐 openapi BadRequestError）。message 透传领域描述，
 * 不回显 client_secret 等敏感值（backend-engineer §3.2）。</p>
 */
public class InvalidCustomOAuthProviderException extends CustomOAuthProviderException {

    /** 稳定业务错误码。 */
    public static final String CODE = "INVALID_CUSTOM_OAUTH_PROVIDER";

    /**
     * @param message 字段级错误描述（不含敏感值）
     */
    public InvalidCustomOAuthProviderException(String message) {
        super(CODE, message);
    }
}
