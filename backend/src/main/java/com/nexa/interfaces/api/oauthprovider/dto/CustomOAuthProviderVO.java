package com.nexa.interfaces.api.oauthprovider.dto;

import com.nexa.domain.oauthprovider.model.CustomOAuthProvider;

import java.util.Arrays;
import java.util.List;

/**
 * 自定义 OAuth provider 视图 DTO（对齐 openapi {@code CustomOAuthProviderVO}，RootAuth）。
 *
 * <p><b>敏感字段零泄露（产品铁律）</b>：本视图<b>绝不</b>包含 {@code client_secret}——openapi 描述明确
 * 「client_secret 受保护」。显式逐字段映射而非反射拷贝，确保聚合新增字段不会默认泄露。{@code scopes}
 * 由领域的空格分隔串拆回数组（对齐 openapi {@code scopes: array}）。</p>
 *
 * @param id                    provider 主键（provider_id）
 * @param name                  路由标识/展示名
 * @param clientId              OAuth client id（非敏感，可回显供 root 核对）
 * @param authorizationEndpoint 授权端点
 * @param tokenEndpoint         令牌端点
 * @param userinfoEndpoint      用户信息端点
 * @param scopes                scope 列表
 */
public record CustomOAuthProviderVO(Long id,
                                      String name,
                                      String clientId,
                                      String authorizationEndpoint,
                                      String tokenEndpoint,
                                      String userinfoEndpoint,
                                      List<String> scopes) {

    /**
     * 从聚合投影为视图 DTO（{@code clientSecret} 在此处<b>根本不读取</b>，从源头杜绝下发）。
     *
     * @param p provider 聚合
     * @return 视图 DTO（无 client_secret）
     */
    public static CustomOAuthProviderVO from(CustomOAuthProvider p) {
        List<String> scopeList = p.scopes() == null || p.scopes().isBlank()
                ? List.of()
                : Arrays.stream(p.scopes().trim().split("\\s+")).toList();
        return new CustomOAuthProviderVO(
                p.id(),
                p.name(),
                p.clientId(),
                p.endpoints().authorizationEndpoint(),
                p.endpoints().tokenEndpoint(),
                p.endpoints().userinfoEndpoint(),
                scopeList);
    }
}
