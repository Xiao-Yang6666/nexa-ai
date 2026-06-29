package com.nexa.infrastructure.passkey.persistence;

import com.nexa.infrastructure.passkey.persistence.entity.PasskeyCredentialJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Passkey 凭据 Spring Data JPA 仓库（基础设施层内部，由 {@code PasskeyCredentialRepositoryImpl} 适配）。
 *
 * <p>仅供基础设施层内部使用，领域/应用层不直接依赖（它们只见领域仓储接口
 * {@code PasskeyCredentialRepository}）。提供按 user_id / credential_id 的派生查询（对齐 DB 唯一索引）。</p>
 */
public interface SpringDataPasskeyCredentialJpaRepository
        extends JpaRepository<PasskeyCredentialJpaEntity, Long> {

    /**
     * 按归属用户查找凭据（user_id 唯一索引，至多一条）。
     *
     * @param userId 用户 id
     * @return 命中返回实体，否则空
     */
    Optional<PasskeyCredentialJpaEntity> findByUserId(Long userId);

    /**
     * 按 credential id 查找凭据（credential_id 唯一索引）。
     *
     * @param credentialId WebAuthn credential id
     * @return 命中返回实体，否则空
     */
    Optional<PasskeyCredentialJpaEntity> findByCredentialId(String credentialId);

    /**
     * 删除指定用户的全部凭据（user_id 唯一，至多删一条）。
     *
     * @param userId 用户 id
     */
    void deleteByUserId(Long userId);
}
