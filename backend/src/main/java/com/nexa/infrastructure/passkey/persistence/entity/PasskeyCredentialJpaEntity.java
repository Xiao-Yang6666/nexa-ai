package com.nexa.infrastructure.passkey.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Passkey 凭据 JPA 持久化实体（基础设施层，对齐 V6 {@code passkey_credentials} / DB-SCHEMA §16）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.passkey.model.PasskeyCredential} 分离
 * （DDD：domain 不感知 JPA）。映射转换在 {@code PasskeyCredentialRepositoryImpl}。
 * {@code publicKey} 落库但绝不进视图 DTO（敏感）。</p>
 */
@Entity
@Table(name = "passkey_credentials",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_passkey_user_id", columnNames = {"user_id"}),
                @UniqueConstraint(name = "ux_passkey_credential_id", columnNames = {"credential_id"})
        })
public class PasskeyCredentialJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "credential_id", nullable = false, length = 512)
    private String credentialId;

    @Column(name = "public_key", nullable = false, columnDefinition = "text")
    private String publicKey;

    @Column(name = "attestation_type", length = 255)
    private String attestationType;

    @Column(name = "aaguid", length = 512)
    private String aaguid;

    @Column(name = "sign_count")
    private Long signCount;

    @Column(name = "clone_warning")
    private Boolean cloneWarning;

    @Column(name = "user_present")
    private Boolean userPresent;

    @Column(name = "user_verified")
    private Boolean userVerified;

    @Column(name = "backup_eligible")
    private Boolean backupEligible;

    @Column(name = "backup_state")
    private Boolean backupState;

    @Column(name = "transports", columnDefinition = "text")
    private String transports;

    @Column(name = "attachment", length = 32)
    private String attachment;

    /** JPA 规范要求的无参构造器。 */
    public PasskeyCredentialJpaEntity() {
    }

    // ---- 访问器（JPA 需要 getter/setter；领域逻辑不在此） ----

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
}
