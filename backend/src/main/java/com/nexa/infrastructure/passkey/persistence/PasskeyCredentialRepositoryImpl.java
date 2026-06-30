package com.nexa.infrastructure.passkey.persistence;

import com.nexa.infrastructure.passkey.persistence.mapper.PasskeyCredentialMapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexa.domain.passkey.exception.InvalidPasskeyCeremonyException;
import com.nexa.domain.passkey.model.PasskeyCredential;
import com.nexa.domain.passkey.repository.PasskeyCredentialRepository;
import com.nexa.domain.passkey.vo.CredentialId;
import com.nexa.infrastructure.passkey.persistence.po.PasskeyCredentialPO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 领域仓储 {@link PasskeyCredentialRepository} 的 MyBatis-Plus 实现（基础设施层适配器，F-1028~1032）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link PasskeyCredentialMapper}
 * + PO 就近工厂方法（{@code PO.of} / {@code po.toDomain}）实现它。领域聚合 {@link PasskeyCredential}
 * 与 PO {@link PasskeyCredentialPO} 分离，domain 因此不感知持久化框架（backend-engineer §2.3）。
 * user_id/credential_id 唯一索引冲突翻译为领域异常（不吞错）。MyBatis-Plus 同样抛 Spring
 * {@code DataIntegrityViolationException}。</p>
 */
@Repository
public class PasskeyCredentialRepositoryImpl implements PasskeyCredentialRepository {

    private final PasskeyCredentialMapper mapper;

    /**
     * @param mapper MyBatis-Plus Mapper（infra 内部依赖）
     */
    public PasskeyCredentialRepositoryImpl(PasskeyCredentialMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public PasskeyCredential save(PasskeyCredential credential) {
        PasskeyCredentialPO po = PasskeyCredentialPO.of(credential);
        try {
            mapper.insert(po);          // 回填自增 id 到 po
            credential.assignId(po.getId());
            return po.toDomain();
        } catch (DataIntegrityViolationException e) {
            // user_id / credential_id 唯一索引冲突：翻译为领域语义（不回显内部约束细节）。
            throw new InvalidPasskeyCeremonyException(
                    "passkey already exists for user or credential id conflict");
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PasskeyCredential> findByUserId(long userId) {
        LambdaQueryWrapper<PasskeyCredentialPO> w = Wrappers.<PasskeyCredentialPO>lambdaQuery()
                .eq(PasskeyCredentialPO::getUserId, userId);
        return Optional.ofNullable(mapper.selectOne(w)).map(PasskeyCredentialPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PasskeyCredential> findByCredentialId(CredentialId credentialId) {
        LambdaQueryWrapper<PasskeyCredentialPO> w = Wrappers.<PasskeyCredentialPO>lambdaQuery()
                .eq(PasskeyCredentialPO::getCredentialId, credentialId.value());
        return Optional.ofNullable(mapper.selectOne(w)).map(PasskeyCredentialPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteByUserId(long userId) {
        // 物理删（原表无软删）；幂等：无记录时 delete 影响 0 行。
        LambdaQueryWrapper<PasskeyCredentialPO> w = Wrappers.<PasskeyCredentialPO>lambdaQuery()
                .eq(PasskeyCredentialPO::getUserId, userId);
        mapper.delete(w);
    }
}
