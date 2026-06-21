package com.nexa.oauthprovider.interfaces.api.dto;

import java.util.List;

/**
 * 创建/更新自定义 OAuth provider 请求 DTO（对齐 openapi {@code CustomOAuthProviderView} 写入语义，F-1024）。
 *
 * <p>字段与 {@link CustomOAuthProviderView} 同形，但写入额外携带敏感的 {@code clientSecret}
 * （openapi 视图 schema 因「client_secret 受保护」不回显该字段，写入时必须由 root 提供；更新时可空=保留原密钥）
 * 与 {@code enabled}（启用态）。{@code id} 用于 PUT 更新定位（POST 创建时为 null）。</p>
 *
 * <p>说明（对 openapi 的合理补全）：openapi 把 POST/PUT requestBody 直接 {@code $ref} 到回显视图 schema，
 * 但回显视图不含 secret——写入显然需要 secret。本 DTO 在保持视图同形的前提下补 {@code clientSecret}/{@code enabled}，
 * 是对契约写入语义的必要补全，不改变回显视图（{@link CustomOAuthProviderView} 仍零密钥）。</p>
 *
 * @param id                    provider 主键（PUT 更新必填，POST 创建为 null）
 * @param name                  路由标识/展示名
 * @param clientId              OAuth client id
 * @param clientSecret          OAuth client secret（创建必填；更新可空=保留原密钥）
 * @param authorizationEndpoint 授权端点
 * @param tokenEndpoint         令牌端点
 * @param userinfoEndpoint      用户信息端点
 * @param scopes                scope 列表（可空）
 * @param enabled               是否启用（缺省 true 由控制器兜底）
 */
public record SaveCustomOAuthProviderRequest(Long id,
                                             String name,
                                             String clientId,
                                             String clientSecret,
                                             String authorizationEndpoint,
                                             String tokenEndpoint,
                                             String userinfoEndpoint,
                                             List<String> scopes,
                                             Boolean enabled) {
}
