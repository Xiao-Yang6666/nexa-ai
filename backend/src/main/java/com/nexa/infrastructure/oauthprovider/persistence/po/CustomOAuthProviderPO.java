package com.nexa.infrastructure.oauthprovider.persistence.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 自定义 OAuth provider JPA 持久化实体（基础设施层，对齐 V4 {@code custom_oauth_providers}）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.oauthprovider.model.CustomOAuthProvider} 分离
 * （DDD：domain 不感知 JPA）。映射转换在 {@code CustomOAuthProviderRepositoryImpl}。
 * {@code clientSecret} 落库但绝不进视图 DTO。</p>
 */
@Entity
@Table(name = "custom_oauth_providers",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_custom_oauth_provider_name", columnNames = {"name"})
        })
public class CustomOAuthProviderPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "client_id", nullable = false, length = 256)
    private String clientId;

    @Column(name = "client_secret", nullable = false, length = 512)
    private String clientSecret;

    @Column(name = "authorization_endpoint", nullable = false, length = 512)
    private String authorizationEndpoint;

    @Column(name = "token_endpoint", nullable = false, length = 512)
    private String tokenEndpoint;

    @Column(name = "userinfo_endpoint", nullable = false, length = 512)
    private String userinfoEndpoint;

    @Column(name = "scopes")
    private String scopes;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;

    /** JPA 规范要求的无参构造器。 */
    public CustomOAuthProviderPO() {
    }

    // ---- 访问器（JPA 需要 getter/setter；领域逻辑不在此） ----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    public void setAuthorizationEndpoint(String authorizationEndpoint) {
        this.authorizationEndpoint = authorizationEndpoint;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getUserinfoEndpoint() {
        return userinfoEndpoint;
    }

    public void setUserinfoEndpoint(String userinfoEndpoint) {
        this.userinfoEndpoint = userinfoEndpoint;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
