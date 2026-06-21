package com.nexa.oauthprovider.domain.exception;

/**
 * 自定义 OAuth provider 不存在异常（领域语义）。
 *
 * <p>抛出场景：按 id / name 定位 provider 失败（更新/删除不存在的 provider、或自定义 provider 登录时
 * provider 名未注册）。接口层映射 404（对齐 openapi NotFoundError）。</p>
 */
public class CustomOAuthProviderNotFoundException extends CustomOAuthProviderException {

    /** 稳定业务错误码。 */
    public static final String CODE = "CUSTOM_OAUTH_PROVIDER_NOT_FOUND";

    /**
     * @param identifier provider 标识（id 或 name），用于排障；不含敏感值
     */
    public CustomOAuthProviderNotFoundException(String identifier) {
        super(CODE, "custom oauth provider not found: " + identifier);
    }
}
