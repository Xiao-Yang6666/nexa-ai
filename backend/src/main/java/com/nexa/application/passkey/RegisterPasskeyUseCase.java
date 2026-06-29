package com.nexa.application.passkey;

import com.nexa.application.passkey.port.PasskeyUserDirectory;
import com.nexa.application.passkey.port.WebAuthnRelyingParty;
import com.nexa.domain.passkey.exception.InvalidPasskeyCeremonyException;
import com.nexa.domain.passkey.model.PasskeyCredential;
import com.nexa.domain.passkey.repository.PasskeyCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passkey 注册用例（应用服务，F-1028 register begin/finish，sessionAuth）。
 *
 * <p>编排 WebAuthn 注册 ceremony 的两段式事务边界：
 * <ul>
 *   <li>{@link #begin(long)}：委托 ceremony 端口生成 {@code CredentialCreationOptions}（含一次性 challenge）。</li>
 *   <li>{@link #finish(long, String)}：委托端口验签 attestation → 用验证结果调聚合 {@code register} 落库。</li>
 * </ul>
 * 验签密码学在端口（基础设施层），领域不变量在聚合（backend-engineer §2.1）。对齐 openapi
 * {@code /api/user/self/passkey/register/begin|finish}。</p>
 */
@Service
public class RegisterPasskeyUseCase {

    private final WebAuthnRelyingParty relyingParty;
    private final PasskeyCredentialRepository repository;
    private final PasskeyUserDirectory userDirectory;

    /**
     * @param relyingParty  WebAuthn ceremony 端口
     * @param repository    passkey 凭据仓储
     * @param userDirectory 账号目录端口（取用户名）
     */
    public RegisterPasskeyUseCase(WebAuthnRelyingParty relyingParty,
                                  PasskeyCredentialRepository repository,
                                  PasskeyUserDirectory userDirectory) {
        this.relyingParty = relyingParty;
        this.repository = repository;
        this.userDirectory = userDirectory;
    }

    /**
     * 注册 begin：生成注册 options（F-1028 begin）。
     *
     * @param userId 会话用户 id
     * @return 可下发前端的 CredentialCreationOptions（JSON 串）
     * @throws InvalidPasskeyCeremonyException 用户不存在
     */
    @Transactional
    public String begin(long userId) {
        String username = userDirectory.findUsernameById(userId);
        if (username == null) {
            // 会话用户在账号域不存在（已删除/非法 id）：拒绝发起 ceremony，不暴露存在性细节。
            throw new InvalidPasskeyCeremonyException("user not found for passkey registration");
        }
        return relyingParty.startRegistration(userId, username).optionsJson();
    }

    /**
     * 注册 finish：验签 attestation 并落库凭据（F-1028 finish）。
     *
     * <p>领域规则：每用户至多一条 passkey（DB user_id 唯一索引）。本切片采取「覆盖」语义——若用户已有
     * 凭据，先删旧再存新（重新注册即替换），避免唯一索引冲突，也符合「单 passkey」产品设定。</p>
     *
     * @param userId                  会话用户 id
     * @param attestationResponseJson 前端回传的 attestation 响应（原始 JSON）
     * @throws InvalidPasskeyCeremonyException 验签失败 / challenge 失效
     */
    @Transactional
    public void finish(long userId, String attestationResponseJson) {
        WebAuthnRelyingParty.RegistrationResult result =
                relyingParty.finishRegistration(userId, attestationResponseJson);

        // 单 passkey 语义：重新注册替换旧凭据（先删后存），规避 user_id/credential_id 唯一索引冲突。
        repository.deleteByUserId(userId);

        PasskeyCredential credential = PasskeyCredential.register(
                userId,
                result.credentialId(),
                result.publicKey(),
                result.attestationType(),
                result.aaguid(),
                result.signCount(),
                result.flags(),
                result.transports(),
                result.attachment());
        repository.save(credential);
    }
}
