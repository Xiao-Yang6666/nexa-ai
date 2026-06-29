package com.nexa.domain.account.model;

import com.nexa.domain.account.exception.InvalidCredentialException;
import com.nexa.domain.account.exception.OAuthBindingConflictException;
import com.nexa.domain.account.vo.OAuthProvider;

import java.time.Instant;
import java.util.Objects;

/**
 * 用户 OAuth 绑定实体（充血领域模型）。
 *
 * <p>表示「某本站用户 ↔ 某第三方 provider 账号」的一条绑定关系（DB-SCHEMA §13
 * UserOAuthBinding / 表 {@code user_oauth_bindings}）。建议作为<b>独立实体 + 独立仓储</b>
 * 而非塞进 User 聚合：绑定是「每用户每 provider 一条」的多对一关联，独立建模便于按
 * (provider, providerUserId) 反查归属用户（OAuth 登录的核心查询），也避免 User 聚合膨胀。</p>
 *
 * <p>充血而非贫血（backend-engineer §2.2）：绑定的不变量（user/provider/providerUserId 必非空、
 * 绑定归属校验）在本实体方法上守护，应用层只编排。本类零框架依赖（不 import JPA/Spring），
 * 与 JPA 实体 {@code UserOAuthBindingPO} 分离，可纯单测。</p>
 *
 * <p>不变量：
 * <ul>
 *   <li>{@code userId} / {@code provider} / {@code providerUserId} 三者均非空（创建时强制）。</li>
 *   <li>绑定一旦建立，{@code provider} 与 {@code providerUserId} 不可变（身份锚点）。</li>
 *   <li>每 provider 一第三方账号唯一（{@code ux_provider_userid}）、每用户每 provider 一条
 *       （{@code ux_user_provider}）——唯一性最终由 DB 复合唯一索引兜底，本实体在
 *       {@link #ensureOwnedBy} 提供应用层可调的归属校验。</li>
 * </ul></p>
 */
public class OAuthBinding {

    /** providerUserId 最大长度，对齐 DB-SCHEMA §13 {@code provider_user_id varchar(256)}。 */
    public static final int PROVIDER_USER_ID_MAX_LENGTH = 256;

    /** 自增主键，未持久化的新绑定为 null。 */
    private Long id;

    /** 绑定归属的本站用户 id（DB-SCHEMA §13 user_id，not null）。 */
    private final long userId;

    /** 第三方 provider（DB-SCHEMA §13 概念上的 provider，本切片以内建枚举落库 provider 标识串）。 */
    private final OAuthProvider provider;

    /** 第三方账号在该 provider 下的唯一标识（DB-SCHEMA §13 provider_user_id，not null）。 */
    private final String providerUserId;

    /**
     * 自定义 provider 的整数主键（V5 {@code provider_ref_id}，对齐 openapi {@code OAuthBindingVO.provider_id} /
     * 解绑端点 {@code {provider_id}}）。内建 provider 绑定为 {@code null}；自定义 provider 绑定存
     * {@code custom_oauth_providers.id}（F-1025/1026/1027）。
     */
    private final Long providerRefId;

    /** 绑定建立时间（DB-SCHEMA §13 created_at）。 */
    private final Instant createdAt;

    private OAuthBinding(Long id, long userId, OAuthProvider provider,
                         String providerUserId, Long providerRefId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.providerRefId = providerRefId;
        this.createdAt = createdAt;
    }

    /**
     * 创建一条新的绑定（工厂方法，充血行为，校验不变量）。
     *
     * <p>领域规则来源：DB-SCHEMA §13 三列均 not null。providerUserId trim 后非空且不超长；
     * 创建时间打当前时刻。唯一性（复合唯一索引）由仓储/DB 兜底，本工厂只保证字段合法。</p>
     *
     * @param userId         绑定归属用户 id（须 &gt; 0，已持久化用户）
     * @param provider       第三方 provider（非空）
     * @param providerUserId 第三方账号 id（非空、非空白、≤ {@value #PROVIDER_USER_ID_MAX_LENGTH}）
     * @return 待持久化的新绑定实体（id 由仓储保存后回填）
     * @throws InvalidCredentialException 当 userId 非正、provider 为空、或 providerUserId 非法时
     */
    public static OAuthBinding create(long userId, OAuthProvider provider, String providerUserId) {
        if (userId <= 0L) {
            throw new InvalidCredentialException("oauth binding requires a persisted user id");
        }
        Objects.requireNonNull(provider, "provider");
        String normalized = providerUserId == null ? null : providerUserId.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new InvalidCredentialException("provider user id must not be blank");
        }
        if (normalized.length() > PROVIDER_USER_ID_MAX_LENGTH) {
            throw new InvalidCredentialException(
                    "provider user id length must be <= " + PROVIDER_USER_ID_MAX_LENGTH);
        }
        return new OAuthBinding(null, userId, provider, normalized, null, Instant.now());
    }

    /** 自定义 provider 在 {@code provider} 列落库的标识前缀（V5 迁移注释约定 {@code custom:<id>}）。 */
    public static final String CUSTOM_PROVIDER_CODE_PREFIX = "custom:";

    /** 自定义 provider 绑定在内建枚举中的占位（落库用 {@code provider} 串的真实标识为 {@code custom:<id>}）。 */
    private static final OAuthProvider CUSTOM_PLACEHOLDER_PROVIDER = OAuthProvider.OIDC;

    /**
     * 创建一条<b>自定义 provider</b> 绑定（工厂方法，充血行为，F-1025）。
     *
     * <p>领域规则：自定义 provider 没有内建枚举，绑定按其整数主键 {@code providerRefId}
     * （{@code custom_oauth_providers.id}）关联，落库时 {@code provider} 列存 {@code custom:<id>}
     * 以保证「每自定义 provider 一账号唯一」的复合唯一索引语义（不同自定义 provider 的 provider 串天然不同）。
     * providerUserId trim 后非空且不超长；创建时间打当前时刻。</p>
     *
     * @param userId         绑定归属用户 id（须 &gt; 0）
     * @param providerRefId  自定义 provider 整数主键（须 &gt; 0）
     * @param providerUserId 第三方账号 id（非空、非空白、≤ {@value #PROVIDER_USER_ID_MAX_LENGTH}）
     * @return 待持久化的新自定义 provider 绑定
     * @throws InvalidCredentialException 当 userId/providerRefId 非正、或 providerUserId 非法时
     */
    public static OAuthBinding createForCustomProvider(long userId, long providerRefId, String providerUserId) {
        if (userId <= 0L) {
            throw new InvalidCredentialException("oauth binding requires a persisted user id");
        }
        if (providerRefId <= 0L) {
            throw new InvalidCredentialException("custom provider binding requires a persisted provider id");
        }
        String normalized = providerUserId == null ? null : providerUserId.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new InvalidCredentialException("provider user id must not be blank");
        }
        if (normalized.length() > PROVIDER_USER_ID_MAX_LENGTH) {
            throw new InvalidCredentialException(
                    "provider user id length must be <= " + PROVIDER_USER_ID_MAX_LENGTH);
        }
        // 自定义 provider 在 provider 列用占位枚举承载（落库串由 RepositoryImpl 据 providerRefId 拼 custom:<id>），
        // 这里以 OIDC 占位仅为满足类型（provider() 不直接用于自定义 provider 的落库标识，见 RepositoryImpl）。
        return new OAuthBinding(null, userId, CUSTOM_PLACEHOLDER_PROVIDER, normalized, providerRefId, Instant.now());
    }

    /**
     * 基础设施层持久化重建专用工厂：从已存数据装配实体（不触发创建不变量与时间打点）。
     *
     * <p>内建 provider 绑定无 {@code providerRefId}，以本重载装配（providerRefId 置 null）。
     * 自定义 provider 绑定走 {@link #rehydrate(Long, long, OAuthProvider, String, Long, Instant)}。</p>
     *
     * @param id             主键
     * @param userId         归属用户 id
     * @param provider       provider 枚举
     * @param providerUserId 第三方账号 id
     * @param createdAt      绑定建立时间
     * @return 重建的绑定实体
     */
    public static OAuthBinding rehydrate(Long id, long userId, OAuthProvider provider,
                                         String providerUserId, Instant createdAt) {
        return new OAuthBinding(id, userId, provider, providerUserId, null, createdAt);
    }

    /**
     * 基础设施层持久化重建专用工厂（含自定义 provider 引用）：从已存数据装配实体。
     *
     * <p>自定义 provider 绑定（V5 {@code provider_ref_id} 非空）走本重载，把整数主键
     * 一并装回，供解绑端点 {@code {provider_id}} 与客户视图 {@code OAuthBindingVO.provider_id} 使用。</p>
     *
     * @param id             主键
     * @param userId         归属用户 id
     * @param provider       provider 枚举（自定义 provider 为占位枚举）
     * @param providerUserId 第三方账号 id
     * @param providerRefId  自定义 provider 整数主键（内建 provider 为 null）
     * @param createdAt      绑定建立时间
     * @return 重建的绑定实体
     */
    public static OAuthBinding rehydrate(Long id, long userId, OAuthProvider provider,
                                         String providerUserId, Long providerRefId, Instant createdAt) {
        return new OAuthBinding(id, userId, provider, providerUserId, providerRefId, createdAt);
    }

    /**
     * 校验本绑定归属于指定用户（绑定冲突护栏，充血行为）。
     *
     * <p>领域规则：OAuth「绑定」流程中，若据 (provider, providerUserId) 反查到的绑定已属于
     * <b>另一个</b>用户，则当前用户不能再绑同一第三方账号（违反每 provider 一账号唯一，
     * DB-SCHEMA §13 ux_provider_userid）。归属一致则幂等通过。</p>
     *
     * @param candidateUserId 期望的归属用户 id
     * @throws OAuthBindingConflictException 当本绑定归属另一用户时
     */
    public void ensureOwnedBy(long candidateUserId) {
        if (this.userId != candidateUserId) {
            throw new OAuthBindingConflictException(provider.code());
        }
    }

    /**
     * 由仓储在保存后回填数据库主键。
     *
     * @param id 数据库自增主键
     */
    public void assignId(Long id) {
        this.id = id;
    }

    /** @return 主键，未持久化为 null */
    public Long id() {
        return id;
    }

    /** @return 绑定归属用户 id */
    public long userId() {
        return userId;
    }

    /** @return 第三方 provider */
    public OAuthProvider provider() {
        return provider;
    }

    /** @return 第三方账号 id（该 provider 下唯一） */
    public String providerUserId() {
        return providerUserId;
    }

    /**
     * @return 自定义 provider 整数主键（{@code custom_oauth_providers.id}），内建 provider 绑定为 null。
     *         对齐 openapi {@code OAuthBindingVO.provider_id} 与解绑端点 {@code {provider_id}}（F-1025/1026/1027）。
     */
    public Long providerRefId() {
        return providerRefId;
    }

    /** @return 绑定建立时间 */
    public Instant createdAt() {
        return createdAt;
    }
}
