package com.nexa.passkey.application;

import com.nexa.passkey.application.port.PasskeyUserDirectory;
import com.nexa.passkey.application.port.WebAuthnRelyingParty;
import com.nexa.passkey.domain.exception.InvalidPasskeyCeremonyException;
import com.nexa.passkey.domain.exception.PasskeyNotFoundException;
import com.nexa.passkey.domain.model.PasskeyCredential;
import com.nexa.passkey.domain.repository.PasskeyCredentialRepository;
import com.nexa.passkey.domain.vo.CredentialId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passkey 无密码登录用例（应用服务，F-1029 login begin/finish，security: []）。
 *
 * <p>编排 WebAuthn 断言 ceremony 的登录两段式：
 * <ul>
 *   <li>{@link #begin(String)}：生成 {@code CredentialRequestOptions}（可指定用户名定位本人凭据，或可发现凭据登录）。</li>
 *   <li>{@link #finish(String)}：验签 assertion → 推进签名计数器/克隆告警 → 返回登录用户快照。</li>
 * </ul>
 * 验签在端口，计数器单调/克隆检测在聚合（backend-engineer §2.1）。对齐 openapi
 * {@code /api/user/passkey/login/begin|finish}（公开端点，登录入口）。</p>
 */
@Service
public class LoginWithPasskeyUseCase {

    private final WebAuthnRelyingParty relyingParty;
    private final PasskeyCredentialRepository repository;
    private final PasskeyUserDirectory userDirectory;

    /**
     * @param relyingParty  WebAuthn ceremony 端口
     * @param repository    passkey 凭据仓储
     * @param userDirectory 账号目录端口
     */
    public LoginWithPasskeyUseCase(WebAuthnRelyingParty relyingParty,
                                   PasskeyCredentialRepository repository,
                                   PasskeyUserDirectory userDirectory) {
        this.relyingParty = relyingParty;
        this.repository = repository;
        this.userDirectory = userDirectory;
    }

    /**
     * 登录 begin：生成断言 options（F-1029 begin）。
     *
     * <p>username 非空时按用户名定位本人凭据，限定 {@code allowCredentialId}（非可发现凭据流程）；
     * 用户名缺失/不存在时退化为不限定凭据（可发现凭据登录，由 authenticator 选择）。begin 不泄露用户名
     * 是否存在（无论存在与否都返回合法 options）。</p>
     *
     * @param username 用户名（可空）
     * @return 可下发前端的 CredentialRequestOptions（JSON 串）
     */
    @Transactional
    public String begin(String username) {
        long userId = 0L;
        CredentialId allow = null;
        if (username != null && !username.isBlank()) {
            Long resolved = userDirectory.findUserIdByUsername(username.trim());
            if (resolved != null) {
                userId = resolved;
                // 已知用户名：若本人有 passkey，限定该凭据；无则仍返回合法 options（不暴露"无 passkey"）。
                allow = repository.findByUserId(resolved)
                        .map(PasskeyCredential::credentialId)
                        .orElse(null);
            }
        }
        return relyingParty
                .startAssertion(WebAuthnRelyingParty.CeremonyPurpose.LOGIN, userId, allow)
                .optionsJson();
    }

    /**
     * 登录 finish：验签 assertion 并产出登录用户（F-1029 finish）。
     *
     * <p>流程：端口验签（公钥经仓储按 credential id 解析）→ 按 credential id 取凭据聚合 → 调
     * {@code recordSuccessfulAssertion} 推进签名计数器并检测克隆 → 保存 → 返回该用户的客户视图快照。
     * 凭据不存在 → {@link PasskeyNotFoundException}；验签失败 → {@link InvalidPasskeyCeremonyException}。</p>
     *
     * @param assertionResponseJson 前端回传的 assertion 响应（原始 JSON）
     * @return 登录用户的中性快照（接口层投影为 UserView）
     * @throws InvalidPasskeyCeremonyException 验签失败 / challenge 失效
     * @throws PasskeyNotFoundException        凭据或用户不存在
     */
    @Transactional
    public PasskeyUserDirectory.UserSnapshot finish(String assertionResponseJson) {
        WebAuthnRelyingParty.AssertionResult result = relyingParty.finishAssertion(
                WebAuthnRelyingParty.CeremonyPurpose.LOGIN,
                assertionResponseJson,
                // 公钥解析回调：按 credential id 从仓储取已存公钥，桥接而不让端口直接依赖仓储。
                credId -> repository.findByCredentialId(credId)
                        .map(PasskeyCredential::publicKey)
                        .orElse(null));

        PasskeyCredential credential = repository.findByCredentialId(result.credentialId())
                .orElseThrow(() -> new PasskeyNotFoundException(
                        "credentialId=" + result.credentialId().value()));

        credential.recordSuccessfulAssertion(result.signCount(), result.flags());
        repository.save(credential);

        PasskeyUserDirectory.UserSnapshot snapshot = userDirectory.findSnapshotById(credential.userId());
        if (snapshot == null) {
            // 凭据归属用户在账号域缺失（数据不一致）：不静默返回半成品，明确报错。
            throw new PasskeyNotFoundException("user not found for credential, userId=" + credential.userId());
        }
        return snapshot;
    }
}
