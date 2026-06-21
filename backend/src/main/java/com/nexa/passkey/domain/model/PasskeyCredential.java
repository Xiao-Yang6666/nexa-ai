package com.nexa.passkey.domain.model;

import com.nexa.passkey.domain.exception.InvalidPasskeyCeremonyException;
import com.nexa.passkey.domain.vo.AuthenticatorFlags;
import com.nexa.passkey.domain.vo.CredentialId;
import com.nexa.passkey.domain.vo.SignCount;

import java.util.Objects;

/**
 * Passkey 凭据聚合根（无密码登录凭据，充血领域模型，F-1028~1032）。
 *
 * <p>WebAuthn 凭据的一致性边界：绑定到某个用户（{@code userId}），持有 authenticator 公钥、唯一
 * credential id、签名计数器与一组行为标志。对齐 DB-SCHEMA §16 {@code passkey_credentials}（唯一
 * {@code user_id}、唯一 {@code credential_id}）——<b>每用户至多一条 passkey</b>（DB user_id 唯一索引兜底，
 * 重复注册由仓储/用例按归属覆盖或拒绝）。</p>
 *
 * <p>零框架依赖（不 import JPA/Spring），与 JPA 实体 {@code PasskeyCredentialJpaEntity} 分离，可纯单测。
 * WebAuthn 的密码学验签（attestation/assertion 验证）<b>不在</b>本聚合内——那属于基础设施层 ceremony
 * 端口（{@code WebAuthnRelyingParty}）职责；本聚合只承载验签<b>通过后</b>的凭据状态与领域不变量
 * （签名计数器单调递增、克隆告警）。</p>
 *
 * <p>不变量：
 * <ul>
 *   <li>{@code userId} 非空（凭据必属于某用户）。</li>
 *   <li>{@code credentialId} 合法（值对象自校验，≤512、非空、唯一由 DB 兜底）。</li>
 *   <li>{@code publicKey} 非空（base64，登录断言验签必需）。</li>
 *   <li>{@code signCount} 单调不回退——回退时置 {@code cloneWarning}（克隆告警，不静默吞）。</li>
 *   <li>{@code publicKey} 为敏感凭据数据——落库但<b>绝不</b>进客户视图（PasskeyView 不读取本字段）。</li>
 * </ul></p>
 */
public class PasskeyCredential {

    /** public_key/transports 以 base64 串存储（DB-SCHEMA §16 text）。此处不限制长度（text 无上限）。 */
    /** attestation_type 最大长度，对齐 DB-SCHEMA §16 {@code varchar(255)}。 */
    public static final int ATTESTATION_TYPE_MAX_LENGTH = 255;

    /** aaguid 最大长度，对齐 DB-SCHEMA §16 {@code varchar(512)}。 */
    public static final int AAGUID_MAX_LENGTH = 512;

    /** attachment 最大长度，对齐 DB-SCHEMA §16 {@code varchar(32)}。 */
    public static final int ATTACHMENT_MAX_LENGTH = 32;

    /** 自增主键，未持久化为 null。 */
    private Long id;

    /** 凭据归属用户 id（逻辑外键 → users.id，唯一）。 */
    private final long userId;

    /** WebAuthn credential id（唯一标识，登录断言据此定位本凭据）。 */
    private final CredentialId credentialId;

    /** authenticator 公钥（base64，验签必需，敏感——绝不下发视图）。 */
    private final String publicKey;

    /** attestation 类型（如 none/packed/...，可空）。 */
    private final String attestationType;

    /** authenticator AAGUID（型号标识 base64，可空）。 */
    private final String aaguid;

    /** 签名计数器（单调递增，克隆检测）。 */
    private SignCount signCount;

    /** 克隆告警标记：一旦检测到计数器回退置真（DB-SCHEMA §16 clone_warning）。 */
    private boolean cloneWarning;

    /** authenticator 行为标志（UP/UV/BE/BS）。 */
    private AuthenticatorFlags flags;

    /** transports（base64 串，可空）。 */
    private final String transports;

    /** authenticator 连接形态（platform/cross-platform，可空）。 */
    private final String attachment;

    private PasskeyCredential(Long id, long userId, CredentialId credentialId, String publicKey,
                              String attestationType, String aaguid, SignCount signCount,
                              boolean cloneWarning, AuthenticatorFlags flags, String transports,
                              String attachment) {
        this.id = id;
        this.userId = userId;
        this.credentialId = credentialId;
        this.publicKey = publicKey;
        this.attestationType = attestationType;
        this.aaguid = aaguid;
        this.signCount = signCount;
        this.cloneWarning = cloneWarning;
        this.flags = flags;
        this.transports = transports;
        this.attachment = attachment;
    }

    /**
     * 注册新 passkey 凭据（工厂方法，充血行为，F-1028 finish）。
     *
     * <p>由应用层在 ceremony 端口完成 attestation 验签<b>之后</b>调用，把验证结果落为领域凭据。
     * 校验 userId/publicKey 非空、credentialId 合法、可选字段长度；初始签名计数器取 authenticator
     * 上报值（可为 0）。唯一性（user_id / credential_id 唯一）由仓储/DB 唯一索引兜底。</p>
     *
     * @param userId          凭据归属用户 id
     * @param credentialId    WebAuthn credential id
     * @param publicKey       authenticator 公钥（base64，敏感）
     * @param attestationType attestation 类型（可空）
     * @param aaguid          authenticator AAGUID（可空）
     * @param initialSignCount 注册时 authenticator 上报的签名计数器
     * @param flags           authenticator 行为标志
     * @param transports      transports（可空）
     * @param attachment      连接形态（可空）
     * @return 待持久化的新凭据（id 由仓储保存后回填）
     * @throws InvalidPasskeyCeremonyException 字段非法
     */
    public static PasskeyCredential register(long userId, CredentialId credentialId, String publicKey,
                                             String attestationType, String aaguid, SignCount initialSignCount,
                                             AuthenticatorFlags flags, String transports, String attachment) {
        requireUserId(userId);
        Objects.requireNonNull(credentialId, "credentialId");
        Objects.requireNonNull(initialSignCount, "initialSignCount");
        Objects.requireNonNull(flags, "flags");
        return new PasskeyCredential(
                null,
                userId,
                credentialId,
                requirePublicKey(publicKey),
                normalizeBounded(attestationType, ATTESTATION_TYPE_MAX_LENGTH, "attestation_type"),
                normalizeBounded(aaguid, AAGUID_MAX_LENGTH, "aaguid"),
                initialSignCount,
                false,
                flags,
                normalizeNullable(transports),
                normalizeBounded(attachment, ATTACHMENT_MAX_LENGTH, "attachment"));
    }

    /**
     * 基础设施层持久化重建专用工厂：从已存数据装配聚合（不触发注册不变量校验）。
     *
     * @param id              主键
     * @param userId          归属用户 id
     * @param credentialId    credential id
     * @param publicKey       公钥（base64）
     * @param attestationType attestation 类型（可空）
     * @param aaguid          AAGUID（可空）
     * @param signCount       签名计数器
     * @param cloneWarning    克隆告警标记
     * @param flags           行为标志
     * @param transports      transports（可空）
     * @param attachment      连接形态（可空）
     * @return 重建的聚合
     */
    public static PasskeyCredential rehydrate(Long id, long userId, CredentialId credentialId, String publicKey,
                                              String attestationType, String aaguid, SignCount signCount,
                                              boolean cloneWarning, AuthenticatorFlags flags, String transports,
                                              String attachment) {
        return new PasskeyCredential(id, userId, credentialId, publicKey, attestationType, aaguid,
                signCount, cloneWarning, flags, transports, attachment);
    }

    /**
     * 记录一次成功的断言（登录/二次验证 finish 验签通过后，充血行为，F-1029/1030）。
     *
     * <p>领域规则（WebAuthn L2 §6.1.1 防克隆/重放）：authenticator 每次断言上报递增的签名计数器。
     * 若新计数器相对已存计数器构成回退（二者均非 0 且新值 ≤ 旧值），置 {@link #cloneWarning} 为真
     * （疑似克隆器，不静默吞——交上层据策略告警/拒绝）。无论是否告警都把计数器推进到新值并刷新标志，
     * 保持与 authenticator 最新状态一致。</p>
     *
     * @param newSignCount authenticator 本次上报的签名计数器
     * @param newFlags     本次断言的行为标志
     */
    public void recordSuccessfulAssertion(SignCount newSignCount, AuthenticatorFlags newFlags) {
        Objects.requireNonNull(newSignCount, "newSignCount");
        Objects.requireNonNull(newFlags, "newFlags");
        if (this.signCount.isCloneWarning(newSignCount)) {
            // 计数器回退：标记克隆告警。保留告警一旦置真不回退（一次可疑即长期标注，便于后续审计/重置）。
            this.cloneWarning = true;
        }
        this.signCount = newSignCount;
        this.flags = newFlags;
    }

    /** 由仓储在保存后回填数据库主键。 @param id 自增主键 */
    public void assignId(Long id) {
        this.id = id;
    }

    private static void requireUserId(long userId) {
        if (userId <= 0) {
            throw new InvalidPasskeyCeremonyException("userId must be positive");
        }
    }

    private static String requirePublicKey(String raw) {
        String v = raw == null ? null : raw.trim();
        if (v == null || v.isEmpty()) {
            throw new InvalidPasskeyCeremonyException("public key must not be blank");
        }
        return v;
    }

    private static String normalizeBounded(String raw, int max, String field) {
        String v = raw == null ? null : raw.trim();
        if (v == null || v.isEmpty()) {
            return null;
        }
        if (v.length() > max) {
            throw new InvalidPasskeyCeremonyException(field + " length must be <= " + max);
        }
        return v;
    }

    private static String normalizeNullable(String raw) {
        String v = raw == null ? null : raw.trim();
        return (v == null || v.isEmpty()) ? null : v;
    }

    // ---- 只读访问器（聚合状态对外只读；publicKey 仅基础设施层持久化/验签用，不进视图） ----

    /** @return 主键，未持久化为 null */
    public Long id() {
        return id;
    }

    /** @return 凭据归属用户 id */
    public long userId() {
        return userId;
    }

    /** @return WebAuthn credential id 值对象 */
    public CredentialId credentialId() {
        return credentialId;
    }

    /** @return authenticator 公钥（base64，敏感，仅基础设施层使用，绝不下发任何视图） */
    public String publicKey() {
        return publicKey;
    }

    /** @return attestation 类型，可空 */
    public String attestationType() {
        return attestationType;
    }

    /** @return authenticator AAGUID，可空 */
    public String aaguid() {
        return aaguid;
    }

    /** @return 签名计数器值对象 */
    public SignCount signCount() {
        return signCount;
    }

    /** @return 是否已标记克隆告警 */
    public boolean cloneWarning() {
        return cloneWarning;
    }

    /** @return authenticator 行为标志 */
    public AuthenticatorFlags flags() {
        return flags;
    }

    /** @return transports（base64 串），可空 */
    public String transports() {
        return transports;
    }

    /** @return authenticator 连接形态，可空 */
    public String attachment() {
        return attachment;
    }
}
