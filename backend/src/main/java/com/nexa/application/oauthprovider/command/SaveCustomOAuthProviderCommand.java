package com.nexa.application.oauthprovider.command;

/**
 * 创建/更新自定义 OAuth provider 命令（接口层翻译后的入参，F-1024）。
 *
 * <p>对齐 openapi {@code CustomOAuthProviderVO}（POST/PUT requestBody）。{@code id} 非空表示更新、
 * 为空表示创建。{@code clientSecret} 为可选——更新时传 null/空白表示保留原密钥（视图不回显密钥，
 * 避免脱敏回显回写清空），创建时必填（聚合校验非空）。{@code scopes} 为客户端传入的 scope 列表，
 * 接口层已拼为空格分隔串。</p>
 *
 * @param id                    provider 主键；null=创建，非空=更新
 * @param name                  provider 路由标识/展示名
 * @param clientId              OAuth client id
 * @param clientSecret          OAuth client secret（创建必填；更新可空=保留原值）
 * @param authorizationEndpoint 授权端点
 * @param tokenEndpoint         令牌端点
 * @param userinfoEndpoint      用户信息端点
 * @param scopes                scope 空格分隔串（可空）
 * @param enabled               是否启用
 */
public record SaveCustomOAuthProviderCommand(Long id,
                                             String name,
                                             String clientId,
                                             String clientSecret,
                                             String authorizationEndpoint,
                                             String tokenEndpoint,
                                             String userinfoEndpoint,
                                             String scopes,
                                             boolean enabled) {
}
