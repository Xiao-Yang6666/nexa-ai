package com.nexa.application.passkey;

import com.nexa.application.passkey.port.WebAuthnRelyingParty;
import com.nexa.domain.passkey.exception.InvalidPasskeyCeremonyException;
import com.nexa.domain.passkey.exception.PasskeyNotFoundException;
import com.nexa.domain.passkey.model.PasskeyCredential;
import com.nexa.domain.passkey.repository.PasskeyCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passkey 二次验证用例（应用服务，F-1030 verify begin/finish，sessionAuth）。
 *
 * <p>已登录用户用 passkey 做敏感操作前的二次验证（step-up）。与登录 ceremony 同为断言流程，区别在于：
 * <ul>
 *   <li>用户已知（会话用户 id），begin 限定本人凭据。</li>
 *   <li>finish 只校验「断言对应的确是本人凭据」，不签发会话（不是登录），仅返回验证通过。</li>
 * </ul>
 * 对齐 openapi {@code /api/user/self/passkey/verify/begin|finish}。本人无 passkey → 404。</p>
 */
@Service
public class VerifyPasskeyUseCase {

    private final WebAuthnRelyingParty relyingParty;
    private final PasskeyCredentialRepository repository;

    /**
     * @param relyingParty WebAuthn ceremony 端口
     * @param repository   passkey 凭据仓储
     */
    public VerifyPasskeyUseCase(WebAuthnRelyingParty relyingParty,
                                PasskeyCredentialRepository repository) {
        this.relyingParty = relyingParty;
        this.repository = repository;
    }

    /**
     * 二次验证 begin：生成断言 options（F-1030 begin）。
     *
     * <p>限定本人 credential id（已知会话用户）。本人无 passkey → 404（无凭据无从二次验证）。</p>
     *
     * @param userId 会话用户 id
     * @return 可下发前端的 CredentialRequestOptions（JSON 串）
     * @throws PasskeyNotFoundException 本人无 passkey
     */
    @Transactional
    public String begin(long userId) {
        PasskeyCredential credential = repository.findByUserId(userId)
                .orElseThrow(() -> new PasskeyNotFoundException("user_id=" + userId));
        return relyingParty.startAssertion(
                        WebAuthnRelyingParty.CeremonyPurpose.SECOND_FACTOR,
                        userId,
                        credential.credentialId())
                .optionsJson();
    }

    /**
     * 二次验证 finish：验签 assertion 并确认归属本人（F-1030 finish）。
     *
     * <p>验签通过后，校验断言对应的 credential id 与本人凭据一致（防止用他人凭据冒充二次验证），
     * 推进签名计数器/克隆检测并保存。不签发会话。</p>
     *
     * @param userId                会话用户 id
     * @param assertionResponseJson 前端回传的 assertion 响应（原始 JSON）
     * @throws PasskeyNotFoundException        本人无 passkey
     * @throws InvalidPasskeyCeremonyException 验签失败 / 断言凭据非本人
     */
    @Transactional
    public void finish(long userId, String assertionResponseJson) {
        PasskeyCredential credential = repository.findByUserId(userId)
                .orElseThrow(() -> new PasskeyNotFoundException("user_id=" + userId));

        WebAuthnRelyingParty.AssertionResult result = relyingParty.finishAssertion(
                WebAuthnRelyingParty.CeremonyPurpose.SECOND_FACTOR,
                assertionResponseJson,
                credId -> repository.findByCredentialId(credId)
                        .map(PasskeyCredential::publicKey)
                        .orElse(null));

        if (!result.credentialId().equals(credential.credentialId())) {
            // 断言用的不是本人那把凭据：拒绝（二次验证必须本人 authenticator）。
            throw new InvalidPasskeyCeremonyException("assertion credential does not belong to current user");
        }

        credential.recordSuccessfulAssertion(result.signCount(), result.flags());
        repository.save(credential);
    }
}
