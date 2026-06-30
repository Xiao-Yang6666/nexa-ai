package com.nexa.interfaces.api.account.dto;

import com.nexa.domain.account.model.OAuthBinding;

import java.time.Instant;

/**
 * 用户 OAuth 绑定视图 DTO（管理端查询 F-1027，对齐 openapi {@code OAuthBindingVO}）。
 *
 * <p>字段严格对齐 openapi schema：{@code id}（绑定主键）、{@code provider_id}（自定义 provider 整数 id）、
 * {@code provider_user_id}（第三方账号 id）、{@code created_at}（绑定建立时间，{@code date-time} ISO-8601）。
 * 经全局 Jackson {@code SNAKE_CASE} 序列化为下划线命名（{@code providerId}→{@code provider_id} 等）。</p>
 *
 * <p>客户/敏感视图铁律（backend-engineer §3.4 / 产品铁律）：本视图<b>绝不</b>下发 access_token、
 * client_secret、上游 provider 配置等敏感字段——只暴露绑定关系的标识三元组 + 时间。{@code providerUserId}
 * 是第三方侧公开标识（非密钥），管理端可见。</p>
 *
 * @param id             绑定主键
 * @param providerId     自定义 provider 整数主键（{@code custom_oauth_providers.id}）；内建 provider 绑定为 null
 * @param providerUserId 第三方账号在该 provider 下的唯一标识
 * @param createdAt      绑定建立时间（ISO-8601 字符串，对齐 openapi {@code date-time}）
 */
public record OAuthBindingVO(
        Long id,
        Long providerId,
        String providerUserId,
        String createdAt) {

    /**
     * 由领域绑定实体投影为管理端视图（敏感字段天然不在领域实体上，无泄露面）。
     *
     * <p>{@code provider_id} 取领域实体的 {@code providerRefId}（自定义 provider 整数 id）；内建 provider
     * 绑定的 {@code providerRefId} 为 null，相应下发 null（openapi schema 未标 required，前端按可空处理）。
     * {@code created_at} 把领域 {@code Instant} 序列化为 ISO-8601 串（对齐 openapi {@code date-time}）。</p>
     *
     * @param binding 领域绑定实体
     * @return 管理端视图
     */
    public static OAuthBindingVO from(OAuthBinding binding) {
        Instant createdAt = binding.createdAt();
        return new OAuthBindingVO(
                binding.id(),
                binding.providerRefId(),
                binding.providerUserId(),
                createdAt == null ? null : createdAt.toString());
    }
}
