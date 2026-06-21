package com.nexa.account.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 用户 OAuth 绑定 JPA 持久化实体（基础设施层，对齐 DB-SCHEMA §11/§13 {@code user_oauth_bindings}）。
 *
 * <p>本实体是<b>持久化映射</b>，与领域实体 {@link com.nexa.account.domain.model.OAuthBinding} 分离
 * （DDD：domain 不感知 JPA）。映射转换在 {@code OAuthBindingRepositoryImpl}。</p>
 *
 * <p>设计说明（对 DB-SCHEMA §13 的合理偏离，已在 {@link com.nexa.account.domain.vo.OAuthProvider}
 * 注释中说明）：DB-SCHEMA §13 的 {@code provider_id} 为整数外键指向现网 {@code CustomOAuthProvider} 表
 * （自定义 provider，非本批 PRD 范围）。本切片绑定的是 4 个内建 provider（github/discord/oidc/linuxdo），
 * 没有 CustomOAuthProvider 行可引用，故 {@code provider} 列用<b>字符串 provider 标识</b>
 * （{@code OAuthProvider.code()}）落库，语义直接、零外键依赖。复合唯一索引相应改为
 * {@code (user_id, provider)} 与 {@code (provider, provider_user_id)}（语义等价 §13 的 ux 约束）。</p>
 */
@Entity
@Table(name = "user_oauth_bindings",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_user_provider", columnNames = {"user_id", "provider"}),
                @UniqueConstraint(name = "ux_provider_userid", columnNames = {"provider", "provider_user_id"})
        },
        indexes = {
                @Index(name = "idx_oauth_bindings_user_id", columnList = "user_id")
        })
public class UserOAuthBindingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 绑定归属的本站用户 id（DB-SCHEMA §13 user_id，not null；逻辑外键 → users.id）。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 第三方 provider 标识串（github/discord/oidc/linuxdo，本切片以字符串落库，见类注释）。 */
    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    /** 第三方账号在该 provider 下的唯一标识（DB-SCHEMA §13 provider_user_id，not null）。 */
    @Column(name = "provider_user_id", nullable = false, length = 256)
    private String providerUserId;

    /**
     * 自定义 OAuth provider 整数主键引用（V5 {@code provider_ref_id}，可空）。
     *
     * <p>自定义 provider 绑定时存 {@code custom_oauth_providers.id}；内建 provider 绑定为 {@code null}。
     * 对齐 openapi {@code OAuthBindingView.provider_id} 与解绑端点 {@code {provider_id}}（F-1025/1026/1027）。</p>
     */
    @Column(name = "provider_ref_id")
    private Long providerRefId;

    /** 绑定建立时间 epoch 秒（DB-SCHEMA §13 created_at）。 */
    @Column(name = "created_at")
    private Long createdAt;

    /** JPA 规范要求的无参构造器。 */
    public UserOAuthBindingJpaEntity() {
    }

    // ---- 访问器（JPA 需要 getter/setter；映射在 RepositoryImpl，领域逻辑不在此） ----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public void setProviderUserId(String providerUserId) {
        this.providerUserId = providerUserId;
    }

    public Long getProviderRefId() {
        return providerRefId;
    }

    public void setProviderRefId(Long providerRefId) {
        this.providerRefId = providerRefId;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
