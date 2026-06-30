package com.nexa.infrastructure.oauthprovider.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.oauthprovider.model.CustomOAuthProvider;
import com.nexa.domain.oauthprovider.vo.OAuthEndpoints;

import java.time.Instant;

/**
 * 自定义 OAuth provider JPA 持久化实体（基础设施层，对齐 V4 {@code custom_oauth_providers}）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.oauthprovider.model.CustomOAuthProvider} 分离
 * （DDD：domain 不感知 JPA）。映射由本类就近工厂方法 {@link #toDomain()} / {@link #of(CustomOAuthProvider)} 承载。
 * {@code clientSecret} 落库但绝不进视图 DTO。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：本 PO 同时保留 JPA 注解（{@code @Entity/@Table/@Column/@Id}，
 * 满足并存期 {@code ddl-auto=validate} 全局启动校验）与 MyBatis-Plus 注解
 * （{@code @TableName/@TableId/@TableField}，供 Mapper 实际读写）。两套注解命名空间独立、互不读取。
 * 阶段4 统一移除 JPA 注解。</p>
 */
@TableName("custom_oauth_providers")
public class CustomOAuthProviderPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("client_id")
    private String clientId;

    @TableField("client_secret")
    private String clientSecret;

    @TableField("authorization_endpoint")
    private String authorizationEndpoint;

    @TableField("token_endpoint")
    private String tokenEndpoint;

    @TableField("userinfo_endpoint")
    private String userinfoEndpoint;

    @TableField("scopes")
    private String scopes;

    @TableField("enabled")
    private boolean enabled;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;

    /** 框架（JPA / MyBatis-Plus）实例化所需的无参构造器。 */
    public CustomOAuthProviderPO() {
    }

    // ---- 访问器（框架需要 getter/setter；领域逻辑不在此） ----

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

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * PO → 领域聚合（重建方向，走 {@link CustomOAuthProvider#rehydrate}/builder）：端点组装为
     * {@link OAuthEndpoints}，{@code createdAt}/{@code updatedAt} 为空兜底（updatedAt 兜底为 createdAt）。
     *
     * @return 重建的领域聚合
     */
    public CustomOAuthProvider toDomain() {
        OAuthEndpoints endpoints = OAuthEndpoints.of(
                authorizationEndpoint, tokenEndpoint, userinfoEndpoint);
        Instant createdAtInstant = createdAt == null ? Instant.now() : Instant.ofEpochSecond(createdAt);
        Instant updatedAtInstant = updatedAt == null ? createdAtInstant : Instant.ofEpochSecond(updatedAt);
        return CustomOAuthProvider.builder()
                .id(id)
                .name(name)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .endpoints(endpoints)
                .scopes(scopes)
                .enabled(enabled)
                .createdAt(createdAtInstant)
                .updatedAt(updatedAtInstant)
                .build();
    }

    /**
     * 领域聚合 → PO（持久化方向）：逐字段映射，端点拆列，{@code createdAt}/{@code updatedAt} 为空兜底为当前 epoch 秒。
     *
     * @param p 领域聚合（非空）
     * @return 待持久化的 PO
     */
    public static CustomOAuthProviderPO of(CustomOAuthProvider p) {
        CustomOAuthProviderPO e = new CustomOAuthProviderPO();
        e.id = p.id();
        e.name = p.name();
        e.clientId = p.clientId();
        e.clientSecret = p.clientSecret();
        e.authorizationEndpoint = p.endpoints().authorizationEndpoint();
        e.tokenEndpoint = p.endpoints().tokenEndpoint();
        e.userinfoEndpoint = p.endpoints().userinfoEndpoint();
        e.scopes = p.scopes();
        e.enabled = p.enabled();
        e.createdAt = p.createdAt() == null ? Instant.now().getEpochSecond() : p.createdAt().getEpochSecond();
        e.updatedAt = p.updatedAt() == null ? Instant.now().getEpochSecond() : p.updatedAt().getEpochSecond();
        return e;
    }
}
