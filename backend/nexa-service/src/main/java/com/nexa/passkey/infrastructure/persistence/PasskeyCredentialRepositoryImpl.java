package com.nexa.passkey.infrastructure.persistence;

import com.nexa.passkey.domain.exception.InvalidPasskeyCeremonyException;
import com.nexa.passkey.domain.model.PasskeyCredential;
import com.nexa.passkey.domain.repository.PasskeyCredentialRepository;
import com.nexa.passkey.domain.vo.AuthenticatorFlags;
import com.nexa.passkey.domain.vo.CredentialId;
import com.nexa.passkey.domain.vo.SignCount;
import com.nexa.passkey.infrastructure.persistence.entity.PasskeyCredentialJpaEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 领域仓储 {@link PasskeyCredentialRepository} 的 JPA 实现（基础设施层适配器，F-1028~1032）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link SpringDataPasskeyCredentialJpaRepository}
 * + 实体↔领域映射实现它。领域聚合 {@link PasskeyCredential} 与 JPA 实体
 * {@link PasskeyCredentialJpaEntity} 分离，映射集中在此处，domain 因此不感知 Hibernate
 * （backend-engineer §2.3）。user_id/credential_id 唯一索引冲突翻译为领域异常（不吞错）。</p>
 */
@Repository
public class PasskeyCredentialRepositoryImpl implements PasskeyCredentialRepository {

    private final SpringDataPasskeyCredentialJpaRepository jpa;

    /**
     * @param jpa Spring Data JPA 仓库（infra 内部依赖）
     */
    public PasskeyCredentialRepositoryImpl(SpringDataPasskeyCredentialJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public PasskeyCredential save(PasskeyCredential credential) {
        PasskeyCredentialJpaEntity entity = toEntity(credential);
        try {
            PasskeyCredentialJpaEntity saved = jpa.saveAndFlush(entity);
            credential.assignId(saved.getId());
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // user_id / credential_id 唯一索引冲突：翻译为领域语义（不回显内部约束细节）。
            throw new InvalidPasskeyCeremonyException(
                    "passkey already exists for user or credential id conflict");
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PasskeyCredential> findByUserId(long userId) {
        return jpa.findByUserId(userId).map(PasskeyCredentialRepositoryImpl::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PasskeyCredential> findByCredentialId(CredentialId credentialId) {
        return jpa.findByCredentialId(credentialId.value()).map(PasskeyCredentialRepositoryImpl::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteByUserId(long userId) {
        // 派生 deleteBy 需在事务内执行（Spring Data 约定）；幂等：无记录时无操作。
        jpa.deleteByUserId(userId);
    }

    // ---- 领域聚合 <-> JPA 实体映射（基础设施层内部，领域不可见） ----

    /**
     * 领域聚合 → JPA 实体（持久化方向）。
     *
     * @param c 领域聚合
     * @return 待持久化的 JPA 实体
     */
    private static PasskeyCredentialJpaEntity toEntity(PasskeyCredential c) {
        PasskeyCredentialJpaEntity e = new PasskeyCredentialJpaEntity();
        e.setId(c.id());
        e.setUserId(c.userId());
        e.setCredentialId(c.credentialId().value());
        e.setPublicKey(c.publicKey());
        e.setAttestationType(c.attestationType());
        e.setAaguid(c.aaguid());
        e.setSignCount(c.signCount().value());
        e.setCloneWarning(c.cloneWarning());
        AuthenticatorFlags flags = c.flags();
        e.setUserPresent(flags.userPresent());
        e.setUserVerified(flags.userVerified());
        e.setBackupEligible(flags.backupEligible());
        e.setBackupState(flags.backupState());
        e.setTransports(c.transports());
        e.setAttachment(c.attachment());
        return e;
    }

    /**
     * JPA 实体 → 领域聚合（重建方向，走 {@link PasskeyCredential#rehydrate}）。
     *
     * @param e JPA 实体
     * @return 重建的领域聚合
     */
    private static PasskeyCredential toDomain(PasskeyCredentialJpaEntity e) {
        SignCount signCount = SignCount.of(e.getSignCount() == null ? 0L : e.getSignCount());
        AuthenticatorFlags flags = new AuthenticatorFlags(
                Boolean.TRUE.equals(e.getUserPresent()),
                Boolean.TRUE.equals(e.getUserVerified()),
                Boolean.TRUE.equals(e.getBackupEligible()),
                Boolean.TRUE.equals(e.getBackupState()));
        return PasskeyCredential.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .credentialId(CredentialId.of(e.getCredentialId()))
                .publicKey(e.getPublicKey())
                .attestationType(e.getAttestationType())
                .aaguid(e.getAaguid())
                .signCount(signCount)
                .cloneWarning(e.getCloneWarning())
                .flags(flags)
                .transports(e.getTransports())
                .attachment(e.getAttachment())
                .build();
    }
}
