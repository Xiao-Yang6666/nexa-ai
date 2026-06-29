package com.nexa.domain.oauthprovider.vo;

import com.nexa.domain.oauthprovider.exception.InvalidCustomOAuthProviderException;

import java.util.Objects;

/**
 * OAuth/OIDC 端点三元组（值对象，不可变、按值相等）。
 *
 * <p>承载一个 OAuth provider 的三个核心端点：授权端点（authorization）、令牌端点（token）、
 * 用户信息端点（userinfo）。既用于 {@link com.nexa.domain.oauthprovider.model.CustomOAuthProvider}
 * 的端点配置，也作为 discovery（F-1023）拉取 issuer well-known 文档后的归一化结果。</p>
 *
 * <p>领域规则来源：openapi {@code POST /api/custom-oauth-provider/discovery} 返回
 * {@code {authorization_endpoint, token_endpoint, userinfo_endpoint}}；
 * {@code CustomOAuthProviderView} 同名字段。值对象保证三端点均为非空且形如 http(s) URL
 * （挡住脏配置进入聚合，backend-engineer §2.4 强烈建议把强约束做成值对象）。</p>
 *
 * @param authorizationEndpoint 授权端点 URL（非空、http(s)）
 * @param tokenEndpoint         令牌端点 URL（非空、http(s)）
 * @param userinfoEndpoint      用户信息端点 URL（非空、http(s)）
 */
public record OAuthEndpoints(String authorizationEndpoint,
                             String tokenEndpoint,
                             String userinfoEndpoint) {

    /** 端点 URL 最大长度，对齐 V4 迁移 {@code varchar(512)}。 */
    public static final int ENDPOINT_MAX_LENGTH = 512;

    /**
     * 紧凑构造器：校验三端点均为合法 http(s) URL 且不超长（不可变值对象的不变量）。
     *
     * @throws InvalidCustomOAuthProviderException 任一端点为空、非 http(s)、或超长
     */
    public OAuthEndpoints {
        authorizationEndpoint = requireHttpUrl(authorizationEndpoint, "authorization_endpoint");
        tokenEndpoint = requireHttpUrl(tokenEndpoint, "token_endpoint");
        userinfoEndpoint = requireHttpUrl(userinfoEndpoint, "userinfo_endpoint");
    }

    /**
     * 工厂方法（与紧凑构造器等价，提供更显式的调用点可读性）。
     *
     * @param authorizationEndpoint 授权端点
     * @param tokenEndpoint         令牌端点
     * @param userinfoEndpoint      用户信息端点
     * @return 校验通过的端点三元组
     * @throws InvalidCustomOAuthProviderException 任一端点非法
     */
    public static OAuthEndpoints of(String authorizationEndpoint,
                                    String tokenEndpoint,
                                    String userinfoEndpoint) {
        return new OAuthEndpoints(authorizationEndpoint, tokenEndpoint, userinfoEndpoint);
    }

    /**
     * 校验单个端点为合法 http(s) URL 并规范化（trim）。
     *
     * <p>为何只做轻量前缀校验而非完整 URL 解析：领域只需挡住明显脏值（空/非 http 协议/超长），
     * 端点的真实可达性是运行时 IO 关注点（拉 token 时自然暴露），不在领域校验范围。</p>
     *
     * @param raw   原始端点串
     * @param field 字段名（错误提示用，不含敏感值）
     * @return 规范化后的端点串
     * @throws InvalidCustomOAuthProviderException 空 / 非 http(s) / 超长
     */
    private static String requireHttpUrl(String raw, String field) {
        String v = raw == null ? null : raw.trim();
        if (v == null || v.isEmpty()) {
            throw new InvalidCustomOAuthProviderException(field + " must not be blank");
        }
        if (!(v.startsWith("http://") || v.startsWith("https://"))) {
            throw new InvalidCustomOAuthProviderException(field + " must be an http(s) URL");
        }
        if (v.length() > ENDPOINT_MAX_LENGTH) {
            throw new InvalidCustomOAuthProviderException(
                    field + " length must be <= " + ENDPOINT_MAX_LENGTH);
        }
        return v;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OAuthEndpoints other)) {
            return false;
        }
        return authorizationEndpoint.equals(other.authorizationEndpoint)
                && tokenEndpoint.equals(other.tokenEndpoint)
                && userinfoEndpoint.equals(other.userinfoEndpoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authorizationEndpoint, tokenEndpoint, userinfoEndpoint);
    }
}
