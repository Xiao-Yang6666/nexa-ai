package com.nexa.oauthprovider.domain.model;

import com.nexa.oauthprovider.domain.exception.InvalidCustomOAuthProviderException;
import com.nexa.oauthprovider.domain.vo.OAuthEndpoints;

import java.time.Instant;
import java.util.Objects;

/**
 * 自定义 OAuth provider 聚合根（充血领域模型，F-1023/1024）。
 *
 * <p>root 管理员配置的第三方 OAuth/OIDC 接入点：name（路由标识）、client 凭证、端点三元组、scopes、启用态。
 * 用户随后可经 {@code /api/oauth/{name}} 走该 provider 登录/绑定（F-1025）。本聚合是 oauthprovider
 * 限界上下文的一致性边界，配置不变量（字段非空/长度/端点合法）在聚合方法上守护（backend-engineer §2.2 充血）。</p>
 *
 * <p>零框架依赖（不 import JPA/Spring），与 JPA 实体 {@code CustomOAuthProviderJpaEntity} 分离，可纯单测。
 * 字段对齐 openapi {@code CustomOAuthProviderView} + V4 迁移 {@code custom_oauth_providers}。</p>
 *
 * <p>不变量：
 * <ul>
 *   <li>{@code name} 非空、≤64、仅含可作路由段的可读字符（作回调路径段 {@code /api/oauth/{name}} + 唯一索引）。</li>
 *   <li>{@code clientId}/{@code clientSecret} 非空（OAuth 换 token 必需）。</li>
 *   <li>{@code endpoints} 为合法端点三元组（值对象自校验）。</li>
 *   <li>{@code clientSecret} 为敏感凭证——落库但<b>绝不</b>进客户/管理视图（视图 DTO 不读取本字段）。</li>
 * </ul></p>
 */
public class CustomOAuthProvider {

    /** name 最大长度，对齐 V4 迁移 {@code varchar(64)}。 */
    public static final int NAME_MAX_LENGTH = 64;

    /** client_id 最大长度，对齐 V4 迁移 {@code varchar(256)}。 */
    public static final int CLIENT_ID_MAX_LENGTH = 256;

    /** client_secret 最大长度，对齐 V4 迁移 {@code varchar(512)}。 */
    public static final int CLIENT_SECRET_MAX_LENGTH = 512;

    /** 自增主键，未持久化为 null（即 openapi provider_id）。 */
    private Long id;

    /** provider 路由标识/展示名（唯一，回调路径段）。 */
    private String name;

    /** OAuth client id。 */
    private String clientId;

    /** OAuth client secret（敏感，绝不下发）。 */
    private String clientSecret;

    /** 授权/令牌/用户信息端点三元组。 */
    private OAuthEndpoints endpoints;

    /** 申请的 scope（空格分隔串，可空）。 */
    private String scopes;

    /** 是否启用（停用后不接受该 provider 登录）。 */
    private boolean enabled;

    /** 创建时间。 */
    private final Instant createdAt;

    /** 最近更新时间。 */
    private Instant updatedAt;

    private CustomOAuthProvider(Long id, String name, String clientId, String clientSecret,
                                OAuthEndpoints endpoints, String scopes, boolean enabled,
                                Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.endpoints = endpoints;
        this.scopes = scopes;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 创建新的自定义 OAuth provider（工厂方法，充血行为，校验全部不变量，F-1024 create）。
     *
     * <p>name/clientId/clientSecret 规范化 + 非空 + 长度校验；endpoints 由值对象自校验。
     * 创建即启用，打创建/更新时间。唯一性（name 唯一）由仓储/DB 唯一索引兜底，本工厂只保证字段合法。</p>
     *
     * @param name         provider 路由标识/展示名
     * @param clientId     OAuth client id
     * @param clientSecret OAuth client secret（敏感）
     * @param endpoints    端点三元组（值对象）
     * @param scopes       scope 串（可空）
     * @return 待持久化的新 provider（id 由仓储保存后回填）
     * @throws InvalidCustomOAuthProviderException 字段非法
     */
    public static CustomOAuthProvider create(String name, String clientId, String clientSecret,
                                             OAuthEndpoints endpoints, String scopes) {
        String n = requireName(name);
        String cid = requireClientId(clientId);
        String secret = requireClientSecret(clientSecret);
        Objects.requireNonNull(endpoints, "endpoints");
        Instant now = Instant.now();
        return new CustomOAuthProvider(null, n, cid, secret, endpoints,
                normalizeScopes(scopes), true, now, now);
    }

    /**
     * 基础设施层持久化重建专用工厂：从已存数据装配聚合（不触发创建不变量与时间打点）。
     *
     * @param id           主键（provider_id）
     * @param name         路由标识
     * @param clientId     client id
     * @param clientSecret client secret
     * @param endpoints    端点三元组
     * @param scopes       scope 串（可空）
     * @param enabled      是否启用
     * @param createdAt    创建时间
     * @param updatedAt    更新时间
     * @return 重建的聚合
     */
    public static CustomOAuthProvider rehydrate(Long id, String name, String clientId, String clientSecret,
                                                OAuthEndpoints endpoints, String scopes, boolean enabled,
                                                Instant createdAt, Instant updatedAt) {
        // 委托 Builder 装配：字段名自解释、null 归一逻辑（如 enabled）收敛在 Builder 一处。
        return builder()
                .id(id)
                .name(name)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .endpoints(endpoints)
                .scopes(scopes)
                .enabled(enabled)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    /**
     * 持久化重建构建器入口（基础设施层 {@code toDomain} 专用）。
     *
     * <p>替代 {@link #rehydrate} 的长位置参数列表：调用处以具名链式方法装配，可读性与抗重构性更好
     * （两个相邻 {@link Instant}（createdAt/updatedAt）位置参数极易写反，Builder 一目了然）。
     * 与 {@code rehydrate} 一致——本入口<b>不</b>触发创建不变量与时间打点，纯还原已存状态。</p>
     *
     * @return 新的 provider 重建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 自定义 OAuth provider 聚合的持久化重建构建器（充血聚合状态对外只读，仅基础设施层重建时经此装配）。
     *
     * <p>设计要点：{@code boolean enabled} 的 setter 接受<b>包装类型</b>并把 {@code null} 归一为
     * false——这样 JPA 实体里可空列的兜底逻辑全部收敛在此，{@code CustomOAuthProviderRepositoryImpl.toDomain}
     * 不再散落 {@code ?:} 三元。值对象 {@link OAuthEndpoints} 与 {@link Instant} 时间整体接收，不拆解。
     * 装配委托私有构造器以兼容 final 字段。</p>
     */
    public static final class Builder {
        private Long id;
        private String name;
        private String clientId;
        private String clientSecret;
        private OAuthEndpoints endpoints;
        private String scopes;
        private boolean enabled;
        private Instant createdAt;
        private Instant updatedAt;

        private Builder() {
        }

        /** @param id 主键（provider_id，未持久化为 null） */
        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        /** @param name provider 路由标识/展示名 */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /** @param clientId OAuth client id */
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        /** @param clientSecret OAuth client secret（敏感） */
        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        /** @param endpoints 端点三元组值对象 */
        public Builder endpoints(OAuthEndpoints endpoints) {
            this.endpoints = endpoints;
            return this;
        }

        /** @param scopes scope 串，可为 null */
        public Builder scopes(String scopes) {
            this.scopes = scopes;
            return this;
        }

        /** @param enabled 是否启用（null 归一为 false） */
        public Builder enabled(Boolean enabled) {
            this.enabled = enabled != null && enabled;
            return this;
        }

        /** @param createdAt 创建时间 */
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /** @param updatedAt 最近更新时间 */
        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /**
         * 装配并返回重建的 provider 聚合（不触发创建不变量与时间打点）。
         *
         * @return 重建的 provider 聚合
         */
        public CustomOAuthProvider build() {
            return new CustomOAuthProvider(id, name, clientId, clientSecret, endpoints,
                    scopes, enabled, createdAt, updatedAt);
        }
    }

    /**
     * 更新可变配置（充血行为，F-1024 update）。
     *
     * <p>领域规则：name/clientId/endpoints/scopes/enabled 可改并经各自校验；clientSecret 为
     * <b>可选更新</b>——传 {@code null}/空白表示「保留原密钥不变」（避免要求 root 每次更新都重输密钥，
     * 也避免视图回显密钥后回写空值清空密钥）。更新打 updatedAt。</p>
     *
     * @param name         新路由标识
     * @param clientId     新 client id
     * @param newSecret    新 client secret（null/空白=保留原密钥）
     * @param endpoints    新端点三元组
     * @param scopes       新 scope 串（可空）
     * @param enabled      新启用态
     * @throws InvalidCustomOAuthProviderException 字段非法
     */
    public void update(String name, String clientId, String newSecret,
                       OAuthEndpoints endpoints, String scopes, boolean enabled) {
        this.name = requireName(name);
        this.clientId = requireClientId(clientId);
        if (newSecret != null && !newSecret.isBlank()) {
            // 仅当显式给出新密钥时才替换；否则保留原密钥（不被空值/脱敏回显覆盖）。
            this.clientSecret = requireClientSecret(newSecret);
        }
        Objects.requireNonNull(endpoints, "endpoints");
        this.endpoints = endpoints;
        this.scopes = normalizeScopes(scopes);
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    /**
     * 校验 provider 当前可用于登录（充血护栏，F-1025）。
     *
     * <p>领域规则：停用（{@code enabled=false}）的 provider 不接受新的登录/绑定（对齐内建 provider 的
     * 「provider 未启用」语义）。停用时抛 {@link InvalidCustomOAuthProviderException}。</p>
     *
     * @throws InvalidCustomOAuthProviderException 当 provider 已停用
     */
    public void ensureEnabledForLogin() {
        if (!enabled) {
            throw new InvalidCustomOAuthProviderException("custom oauth provider is disabled: " + name);
        }
    }

    /** 由仓储在保存后回填数据库主键。 @param id 自增主键 */
    public void assignId(Long id) {
        this.id = id;
    }

    private static String requireName(String raw) {
        String v = raw == null ? null : raw.trim();
        if (v == null || v.isEmpty()) {
            throw new InvalidCustomOAuthProviderException("name must not be blank");
        }
        if (v.length() > NAME_MAX_LENGTH) {
            throw new InvalidCustomOAuthProviderException("name length must be <= " + NAME_MAX_LENGTH);
        }
        // name 作回调路径段 + 路由 key：限制为字母/数字/下划线/连字符，避免路径注入与歧义。
        if (!v.matches("[A-Za-z0-9_-]+")) {
            throw new InvalidCustomOAuthProviderException(
                    "name must contain only letters, digits, underscore or hyphen");
        }
        return v;
    }

    private static String requireClientId(String raw) {
        String v = raw == null ? null : raw.trim();
        if (v == null || v.isEmpty()) {
            throw new InvalidCustomOAuthProviderException("client_id must not be blank");
        }
        if (v.length() > CLIENT_ID_MAX_LENGTH) {
            throw new InvalidCustomOAuthProviderException(
                    "client_id length must be <= " + CLIENT_ID_MAX_LENGTH);
        }
        return v;
    }

    private static String requireClientSecret(String raw) {
        String v = raw == null ? null : raw.trim();
        if (v == null || v.isEmpty()) {
            throw new InvalidCustomOAuthProviderException("client_secret must not be blank");
        }
        if (v.length() > CLIENT_SECRET_MAX_LENGTH) {
            throw new InvalidCustomOAuthProviderException(
                    "client_secret length must be <= " + CLIENT_SECRET_MAX_LENGTH);
        }
        return v;
    }

    /**
     * 规范化 scopes：trim，空白归一为 null。
     *
     * @param raw 原始 scope 串
     * @return 规范化后的 scope 串（可空）
     */
    private static String normalizeScopes(String raw) {
        String v = raw == null ? null : raw.trim();
        return (v == null || v.isEmpty()) ? null : v;
    }

    // ---- 只读访问器（聚合状态对外只读；clientSecret 仅基础设施层持久化/换 token 用，不进视图） ----

    /** @return 主键（provider_id），未持久化为 null */
    public Long id() {
        return id;
    }

    /** @return provider 路由标识/展示名 */
    public String name() {
        return name;
    }

    /** @return OAuth client id */
    public String clientId() {
        return clientId;
    }

    /** @return OAuth client secret（敏感，仅基础设施层使用，绝不下发到任何视图） */
    public String clientSecret() {
        return clientSecret;
    }

    /** @return 端点三元组 */
    public OAuthEndpoints endpoints() {
        return endpoints;
    }

    /** @return scope 串，可空 */
    public String scopes() {
        return scopes;
    }

    /** @return 是否启用 */
    public boolean enabled() {
        return enabled;
    }

    /** @return 创建时间 */
    public Instant createdAt() {
        return createdAt;
    }

    /** @return 最近更新时间 */
    public Instant updatedAt() {
        return updatedAt;
    }
}
