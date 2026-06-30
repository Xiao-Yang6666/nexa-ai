package com.nexa.infrastructure.passkey.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.passkey.model.PasskeyCredential;
import com.nexa.domain.passkey.vo.AuthenticatorFlags;
import com.nexa.domain.passkey.vo.CredentialId;
import com.nexa.domain.passkey.vo.SignCount;

/**
 * Passkey 凭据 JPA 持久化实体（基础设施层，对齐 V6 {@code passkey_credentials} / DB-SCHEMA §16）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.passkey.model.PasskeyCredential} 分离
 * （DDD：domain 不感知 JPA）。映射由本类就近工厂方法 {@link #toDomain()} / {@link #of(PasskeyCredential)} 承载。
 * {@code publicKey} 落库但绝不进视图 DTO（敏感）。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：本 PO 同时保留 JPA 注解（{@code @Entity/@Table/@Column/@Id}，
 * 满足并存期 {@code ddl-auto=validate} 全局启动校验）与 MyBatis-Plus 注解
 * （{@code @TableName/@TableId/@TableField}，供 Mapper 实际读写）。两套注解命名空间独立、互不读取。
 * 阶段4 统一移除 JPA 注解。</p>
 */
@TableName("passkey_credentials")
public class PasskeyCredentialPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("credential_id")
    private String credentialId;

    @TableField("public_key")
    private String publicKey;

    @TableField("attestation_type")
    private String attestationType;

    @TableField("aaguid")
    private String aaguid;

    @TableField("sign_count")
    private Long signCount;

    @TableField("clone_warning")
    private Boolean cloneWarning;

    @TableField("user_present")
    private Boolean userPresent;

    @TableField("user_verified")
    private Boolean userVerified;

    @TableField("backup_eligible")
    private Boolean backupEligible;

    @TableField("backup_state")
    private Boolean backupState;

    @TableField("transports")
    private String transports;

    @TableField("attachment")
    private String attachment;

    /** 框架（JPA / MyBatis-Plus）实例化所需的无参构造器。 */
    public PasskeyCredentialPO() {
    }

    // ---- 访问器（框架需要 getter/setter；领域逻辑不在此） ----

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

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getAttestationType() {
        return attestationType;
    }

    public void setAttestationType(String attestationType) {
        this.attestationType = attestationType;
    }

    public String getAaguid() {
        return aaguid;
    }

    public void setAaguid(String aaguid) {
        this.aaguid = aaguid;
    }

    public Long getSignCount() {
        return signCount;
    }

    public void setSignCount(Long signCount) {
        this.signCount = signCount;
    }

    public Boolean getCloneWarning() {
        return cloneWarning;
    }

    public void setCloneWarning(Boolean cloneWarning) {
        this.cloneWarning = cloneWarning;
    }

    public Boolean getUserPresent() {
        return userPresent;
    }

    public void setUserPresent(Boolean userPresent) {
        this.userPresent = userPresent;
    }

    public Boolean getUserVerified() {
        return userVerified;
    }

    public void setUserVerified(Boolean userVerified) {
        this.userVerified = userVerified;
    }

    public Boolean getBackupEligible() {
        return backupEligible;
    }

    public void setBackupEligible(Boolean backupEligible) {
        this.backupEligible = backupEligible;
    }

    public Boolean getBackupState() {
        return backupState;
    }

    public void setBackupState(Boolean backupState) {
        this.backupState = backupState;
    }

    public String getTransports() {
        return transports;
    }

    public void setTransports(String transports) {
        this.transports = transports;
    }

    public String getAttachment() {
        return attachment;
    }

    public void setAttachment(String attachment) {
        this.attachment = attachment;
    }

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * PO → 领域聚合（重建方向，走 {@link PasskeyCredential#builder}）：{@code signCount} 为空兜底为 0，
     * 各 flag 用 {@link Boolean#TRUE}.equals 兜底为 false。
     *
     * @return 重建的领域聚合
     */
    public PasskeyCredential toDomain() {
        SignCount signCountVo = SignCount.of(signCount == null ? 0L : signCount);
        AuthenticatorFlags flags = new AuthenticatorFlags(
                Boolean.TRUE.equals(userPresent),
                Boolean.TRUE.equals(userVerified),
                Boolean.TRUE.equals(backupEligible),
                Boolean.TRUE.equals(backupState));
        return PasskeyCredential.builder()
                .id(id)
                .userId(userId)
                .credentialId(CredentialId.of(credentialId))
                .publicKey(publicKey)
                .attestationType(attestationType)
                .aaguid(aaguid)
                .signCount(signCountVo)
                .cloneWarning(cloneWarning)
                .flags(flags)
                .transports(transports)
                .attachment(attachment)
                .build();
    }

    /**
     * 领域聚合 → PO（持久化方向）：逐字段映射，flags 拆为四个布尔列。
     *
     * @param c 领域聚合（非空）
     * @return 待持久化的 PO
     */
    public static PasskeyCredentialPO of(PasskeyCredential c) {
        PasskeyCredentialPO e = new PasskeyCredentialPO();
        e.id = c.id();
        e.userId = c.userId();
        e.credentialId = c.credentialId().value();
        e.publicKey = c.publicKey();
        e.attestationType = c.attestationType();
        e.aaguid = c.aaguid();
        e.signCount = c.signCount().value();
        e.cloneWarning = c.cloneWarning();
        AuthenticatorFlags flags = c.flags();
        e.userPresent = flags.userPresent();
        e.userVerified = flags.userVerified();
        e.backupEligible = flags.backupEligible();
        e.backupState = flags.backupState();
        e.transports = c.transports();
        e.attachment = c.attachment();
        return e;
    }
}
