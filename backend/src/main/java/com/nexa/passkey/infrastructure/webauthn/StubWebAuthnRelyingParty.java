package com.nexa.passkey.infrastructure.webauthn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.passkey.application.port.PasskeyChallengeStore;
import com.nexa.passkey.application.port.WebAuthnRelyingParty;
import com.nexa.passkey.domain.exception.InvalidPasskeyCeremonyException;
import com.nexa.passkey.domain.vo.AuthenticatorFlags;
import com.nexa.passkey.domain.vo.CredentialId;
import com.nexa.passkey.domain.vo.SignCount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WebAuthn relying party 端口的<b>桩实现</b>（基础设施层，F-1028~1030）。
 *
 * <p><b>状态声明</b>：本类为<b>结构完整的桩</b>，<b>尚未做真实密码学验签</b>。它：
 * <ul>
 *   <li>begin：生成<b>真实一次性 challenge</b>（SecureRandom + base64url）并经 {@link PasskeyChallengeStore}
 *       暂存，产出结构合法、可被浏览器 WebAuthn API 消费的 options JSON（rp/user/challenge/pubKeyCredParams）。</li>
 *   <li>finish：消费 challenge（缺失/过期即拒，<b>这部分防重放是真的</b>），并从前端响应 JSON 解析出
 *       credential id / 公钥 / 签名计数器 / authenticator 标志——但<b>不验证 attestation/assertion 的密码学签名</b>。</li>
 * </ul>
 * 即「ceremony 编排骨架真实、challenge 生命周期真实、验签为占位」。</p>
 *
 * <p>TODO(F-1028~1030 真实验签): 引入 <b>webauthn4j</b>（{@code com.webauthn4j:webauthn4j-core}）：
 * <ul>
 *   <li>begin 用 {@code PublicKeyCredentialCreationOptions}/{@code ...RequestOptions} 构造，序列化下发。</li>
 *   <li>finish 用 {@code WebAuthnRegistrationManager.verify(...)} / {@code WebAuthnAuthenticationManager.verify(...)}
 *       做来源(origin)/RP ID hash/challenge/签名/计数器的完整验签，验签失败抛 {@link InvalidPasskeyCeremonyException}。</li>
 *   <li>RP ID / origin / challenge 长度等从配置注入（{@code nexa.passkey.*}）。</li>
 * </ul>
 * 端口契约已按真实库语义设计，替换实现不动应用层（backend-engineer §2.3）。</p>
 */
@Component
public class StubWebAuthnRelyingParty implements WebAuthnRelyingParty {

    /** challenge 字节数（WebAuthn 推荐 ≥16，此处 32）。 */
    private static final int CHALLENGE_BYTES = 32;

    private final PasskeyChallengeStore challengeStore;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder base64Url = Base64.getUrlEncoder().withoutPadding();

    /** Relying Party ID（域名），从配置注入；缺省 localhost 便于本地验证。 */
    private final String rpId;

    /** Relying Party 展示名。 */
    private final String rpName;

    /**
     * @param challengeStore challenge 暂存端口
     * @param objectMapper   Jackson（Spring Boot 自动装配）
     * @param rpId           RP ID（配置 {@code nexa.passkey.rp-id}，缺省 localhost）
     * @param rpName         RP 展示名（配置 {@code nexa.passkey.rp-name}，缺省 Nexa）
     */
    public StubWebAuthnRelyingParty(PasskeyChallengeStore challengeStore,
                                    ObjectMapper objectMapper,
                                    @Value("${nexa.passkey.rp-id:localhost}") String rpId,
                                    @Value("${nexa.passkey.rp-name:Nexa}") String rpName) {
        this.challengeStore = challengeStore;
        this.objectMapper = objectMapper;
        this.rpId = rpId;
        this.rpName = rpName;
    }

    /** {@inheritDoc} */
    @Override
    public RegistrationOptions startRegistration(long userId, String username) {
        String challenge = newChallenge();
        challengeStore.put(registrationKey(userId), challenge);

        // 结构合法的 CredentialCreationOptions（浏览器可消费）。pubKeyCredParams 取 ES256(-7)/RS256(-257)。
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("challenge", challenge);
        options.put("rp", Map.of("id", rpId, "name", rpName));
        options.put("user", Map.of(
                "id", base64Url.encodeToString(Long.toString(userId).getBytes()),
                "name", username,
                "displayName", username));
        options.put("pubKeyCredParams", List.of(
                Map.of("type", "public-key", "alg", -7),
                Map.of("type", "public-key", "alg", -257)));
        options.put("timeout", 300_000);
        options.put("attestation", "none");
        return new RegistrationOptions(writeJson(options));
    }

    /** {@inheritDoc} */
    @Override
    public RegistrationResult finishRegistration(long userId, String attestationResponseJson) {
        // 防重放：challenge 一次性消费，缺失/过期即拒（这部分是真实的）。
        String challenge = challengeStore.consume(registrationKey(userId));
        if (challenge == null) {
            throw new InvalidPasskeyCeremonyException("registration challenge missing or expired");
        }
        JsonNode root = parse(attestationResponseJson);

        // TODO(webauthn4j): 此处应做 attestation 完整验签（origin/rpIdHash/challenge 匹配/签名）。
        // 桩实现仅解析必要字段，结构缺失即拒（不静默接受空凭据）。
        CredentialId credentialId = CredentialId.of(requireText(root, "id"));
        String publicKey = optText(root, "publicKey");
        if (publicKey == null || publicKey.isBlank()) {
            // attestation 未携带公钥（桩用 response.publicKey 或顶层 publicKey 占位）。
            publicKey = optTextAt(root, "response", "publicKey");
        }
        if (publicKey == null || publicKey.isBlank()) {
            throw new InvalidPasskeyCeremonyException("attestation response missing public key");
        }
        long signCount = optLongAt(root, "response", "signCount", 0L);
        return new RegistrationResult(
                credentialId,
                publicKey,
                optTextAt(root, "response", "attestationType"),
                optTextAt(root, "response", "aaguid"),
                SignCount.of(signCount),
                parseFlags(root),
                optTextAt(root, "response", "transports"),
                optText(root, "authenticatorAttachment"));
    }

    /** {@inheritDoc} */
    @Override
    public AssertionOptions startAssertion(CeremonyPurpose purpose, long userId, CredentialId allowCredentialId) {
        String challenge = newChallenge();
        challengeStore.put(assertionKey(purpose, userId), challenge);

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("challenge", challenge);
        options.put("rpId", rpId);
        options.put("timeout", 300_000);
        options.put("userVerification", purpose == CeremonyPurpose.SECOND_FACTOR ? "required" : "preferred");
        if (allowCredentialId != null) {
            options.put("allowCredentials", List.of(
                    Map.of("type", "public-key", "id", allowCredentialId.value())));
        }
        return new AssertionOptions(writeJson(options));
    }

    /** {@inheritDoc} */
    @Override
    public AssertionResult finishAssertion(CeremonyPurpose purpose, String assertionResponseJson,
                                           PublicKeyResolver publicKeyResolver) {
        JsonNode root = parse(assertionResponseJson);
        CredentialId credentialId = CredentialId.of(requireText(root, "id"));

        // 防重放：先消费对应 purpose 的 challenge。assertion 不携带 userId，故 key 用 purpose 维度
        // （登录场景 userId=0 维度），缺失/过期即拒。
        long keyUserId = purpose == CeremonyPurpose.SECOND_FACTOR
                ? optLong(root, "userId", 0L)
                : 0L;
        String challenge = challengeStore.consume(assertionKey(purpose, keyUserId));
        if (challenge == null) {
            throw new InvalidPasskeyCeremonyException("assertion challenge missing or expired");
        }

        // 公钥必须能按 credential id 解析到（凭据存在的前置校验，验签的输入）。
        String publicKey = publicKeyResolver.resolve(credentialId);
        if (publicKey == null) {
            throw new InvalidPasskeyCeremonyException("no registered passkey for assertion credential");
        }

        // TODO(webauthn4j): 此处应用 publicKey 做 assertion 完整验签（clientDataJSON/authenticatorData/signature）。
        long signCount = optLongAt(root, "response", "signCount", 0L);
        return new AssertionResult(credentialId, SignCount.of(signCount), parseFlags(root));
    }

    // ---- 内部工具 ----

    private String newChallenge() {
        byte[] bytes = new byte[CHALLENGE_BYTES];
        secureRandom.nextBytes(bytes);
        return base64Url.encodeToString(bytes);
    }

    private static String registrationKey(long userId) {
        return "register:" + userId;
    }

    private static String assertionKey(CeremonyPurpose purpose, long userId) {
        return purpose.name().toLowerCase() + ":" + userId;
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            throw new InvalidPasskeyCeremonyException("ceremony response body must not be empty");
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            // 解析失败 wrap 为领域异常带上下文，不吞错也不裸抛 Jackson 异常（backend-engineer §3.2）。
            throw new InvalidPasskeyCeremonyException("invalid ceremony response json: " + e.getMessage());
        }
    }

    private AuthenticatorFlags parseFlags(JsonNode root) {
        JsonNode flags = root.path("response").path("flags");
        if (flags.isMissingNode() || !flags.isObject()) {
            // 无标志来源：保守缺省（仅 UP），不臆造 UV/备份态。
            return AuthenticatorFlags.userPresentOnly();
        }
        return new AuthenticatorFlags(
                flags.path("userPresent").asBoolean(true),
                flags.path("userVerified").asBoolean(false),
                flags.path("backupEligible").asBoolean(false),
                flags.path("backupState").asBoolean(false));
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) {
            throw new InvalidPasskeyCeremonyException("ceremony response missing field: " + field);
        }
        return v.asText();
    }

    private static String optText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private static String optTextAt(JsonNode node, String parent, String field) {
        JsonNode v = node.path(parent).get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private static long optLong(JsonNode node, String field, long def) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? def : v.asLong(def);
    }

    private static long optLongAt(JsonNode node, String parent, String field, long def) {
        JsonNode v = node.path(parent).get(field);
        return (v == null || v.isNull()) ? def : v.asLong(def);
    }

    private String writeJson(Map<String, Object> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            throw new InvalidPasskeyCeremonyException("failed to serialize ceremony options: " + e.getMessage());
        }
    }
}
