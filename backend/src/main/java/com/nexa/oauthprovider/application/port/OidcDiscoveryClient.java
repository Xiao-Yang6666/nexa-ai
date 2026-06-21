package com.nexa.oauthprovider.application.port;

import com.nexa.oauthprovider.domain.vo.OAuthEndpoints;

/**
 * OIDC discovery 端口（应用层定义，基础设施层用 HTTP client 实现，F-1023）。
 *
 * <p>承载「拉取 issuer 的 well-known discovery 文档并归一化为端点三元组」这一 IO 能力。
 * OIDC 规范约定 {@code {issuer}/.well-known/openid-configuration} 返回含
 * {@code authorization_endpoint/token_endpoint/userinfo_endpoint} 的 JSON。应用层用例只依赖本端口，
 * 真实 HTTP 拉取与解析封装在基础设施实现（backend-engineer §2.3 依赖倒置）。</p>
 */
public interface OidcDiscoveryClient {

    /**
     * 拉取并归一化指定 issuer 的 OIDC discovery 端点。
     *
     * <p>实现负责：① 由 issuer 拼出 {@code /.well-known/openid-configuration}（已含则不重复拼）；
     * ② GET 拉取 JSON；③ 取 authorization/token/userinfo 三端点归一化为 {@link OAuthEndpoints}。
     * 网络/解析失败应 wrap 带 issuer 上下文向上抛（不吞错），由上层翻译为对用户的失败响应。</p>
     *
     * @param issuer OIDC issuer 基址（如 {@code https://accounts.example.com}）
     * @return 归一化后的端点三元组
     */
    OAuthEndpoints fetch(String issuer);
}
