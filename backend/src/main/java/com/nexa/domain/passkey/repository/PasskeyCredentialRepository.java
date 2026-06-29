package com.nexa.domain.passkey.repository;

import com.nexa.domain.passkey.model.PasskeyCredential;
import com.nexa.domain.passkey.vo.CredentialId;

import java.util.Optional;

/**
 * Passkey 凭据聚合仓储接口（领域层定义，基础设施层实现，F-1028~1032）。
 *
 * <p>DDD 依赖倒置：domain 只声明「需要什么持久化能力」，不关心 JPA/SQL 细节（backend-engineer §2.3）。
 * 应用层用例仅依赖本接口，单测可注入桩/mock，无需起 DB。实现见
 * {@code infrastructure.persistence.PasskeyCredentialRepositoryImpl}。</p>
 *
 * <p>DB-SCHEMA §16 唯一约束（{@code user_id} 唯一、{@code credential_id} 唯一）决定了查询入口：
 * 按 userId（本人状态/二次验证/管理端重置）与按 credentialId（登录断言定位公钥）两条主路径。</p>
 */
public interface PasskeyCredentialRepository {

    /**
     * 持久化凭据（新增或更新）。
     *
     * <p>新凭据保存后回填数据库生成的自增 id。user_id/credential_id 唯一索引冲突由实现翻译为领域异常。</p>
     *
     * @param credential 待保存的凭据聚合
     * @return 持久化后的凭据（含 id）
     */
    PasskeyCredential save(PasskeyCredential credential);

    /**
     * 按归属用户查找凭据（本人查询 F-1031、二次验证 F-1030、管理端重置 F-1032）。
     *
     * @param userId 用户 id
     * @return 命中返回凭据聚合，否则空
     */
    Optional<PasskeyCredential> findByUserId(long userId);

    /**
     * 按 credential id 查找凭据（登录断言 F-1029 据此定位用户公钥）。
     *
     * @param credentialId WebAuthn credential id
     * @return 命中返回凭据聚合，否则空
     */
    Optional<PasskeyCredential> findByCredentialId(CredentialId credentialId);

    /**
     * 删除指定用户的 passkey（本人删除 F-1031 / 管理端重置 F-1032）。
     *
     * <p>幂等：目标用户无凭据时静默无操作（重置语义对「本就没有」也视为成功）。</p>
     *
     * @param userId 用户 id
     */
    void deleteByUserId(long userId);
}
