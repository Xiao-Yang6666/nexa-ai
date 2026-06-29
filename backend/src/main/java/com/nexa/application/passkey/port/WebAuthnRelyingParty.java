package com.nexa.application.passkey.port;

import com.nexa.domain.passkey.vo.AuthenticatorFlags;
import com.nexa.domain.passkey.vo.CredentialId;
import com.nexa.domain.passkey.vo.SignCount;

/**
 * WebAuthn relying party 端口（应用层依赖，基础设施层实现 ceremony 密码学）。
 *
 * <p>把 WebAuthn 两段式 ceremony（begin 生成 options / finish 验签）抽象为端口，使应用层用例不直接
 * 依赖具体 WebAuthn 库（如 webauthn4j）——可替换实现、可在单测注入桩（backend-engineer §2.3 依赖倒置）。
 * domain 聚合只承载验签<b>通过后</b>的状态，验签密码学全在本端口的实现里。</p>
 *
 * <p><b>当前实现状态</b>：基础设施层提供桩实现 {@code StubWebAuthnRelyingParty}（生成结构合法的 options、
 * 对 finish 做最小结构校验），真实验签待引入 webauthn4j（见实现类 TODO）。端口契约即按真实库语义设计，
 * 后续替换实现不动应用层。</p>
 */
public interface WebAuthnRelyingParty {

    /**
     * 生成注册 ceremony 的 {@code CredentialCreationOptions}（F-1028 begin）。
     *
     * <p>实现应生成一次性 challenge 并交由 {@link PasskeyChallengeStore} 暂存（与本次用户/用途绑定），
     * 返回值为可直接下发前端的 options（含 challenge、rp、user、pubKeyCredParams 等）。</p>
     *
     * @param userId   发起注册的会话用户 id
     * @param username 用户名（作为 WebAuthn user.name 展示）
     * @return 可序列化为 JSON 下发前端的注册 options
     */
    RegistrationOptions startRegistration(long userId, String username);

    /**
     * 校验注册 ceremony 的 attestation 响应并产出凭据数据（F-1028 finish）。
     *
     * <p>实现应：取出并消费暂存 challenge → 验证 attestation（来源/签名/challenge 匹配）→ 解析公钥与
     * authenticator 数据。验签失败抛 {@code InvalidPasskeyCeremonyException}（不吞错）。</p>
     *
     * @param userId               会话用户 id（challenge 归属校验）
     * @param attestationResponseJson 前端回传的 attestation 响应（原始 JSON）
     * @return 验签通过后的凭据数据，供聚合 {@code register} 落库
     */
    RegistrationResult finishRegistration(long userId, String attestationResponseJson);

    /**
     * 生成断言 ceremony 的 {@code CredentialRequestOptions}（F-1029/1030 begin）。
     *
     * <p>实现生成一次性 challenge 暂存（与 purpose 绑定：登录或二次验证），返回可下发前端的 options。
     * {@code allowCredentialId} 可空（无用户名的可发现凭据登录）或指定（二次验证已知本人凭据）。</p>
     *
     * @param purpose           ceremony 用途（登录 / 二次验证）
     * @param userId            已知用户 id（二次验证场景为本人；登录场景可为 0 表示未定位）
     * @param allowCredentialId 限定的 credential id（可空）
     * @return 可序列化为 JSON 下发前端的断言 options
     */
    AssertionOptions startAssertion(CeremonyPurpose purpose, long userId, CredentialId allowCredentialId);

    /**
     * 校验断言 ceremony 的 assertion 响应（F-1029 登录 / F-1030 二次验证 finish）。
     *
     * <p>实现应：解析 assertion 取出 credential id → 取出并消费暂存 challenge → 用对应凭据公钥验签
     * （公钥经 {@code publicKeyResolver} 按 credential id 取得）→ 返回 credential id、新签名计数器与标志。
     * 验签失败抛 {@code InvalidPasskeyCeremonyException}。</p>
     *
     * @param purpose              ceremony 用途
     * @param assertionResponseJson 前端回传的 assertion 响应（原始 JSON）
     * @param publicKeyResolver    按 credential id 解析公钥（base64）的回调；返回 null 表示凭据不存在
     * @return 验签通过后的断言结果
     */
    AssertionResult finishAssertion(CeremonyPurpose purpose, String assertionResponseJson,
                                    PublicKeyResolver publicKeyResolver);

    /**
     * 断言 ceremony 用途。
     */
    enum CeremonyPurpose {
        /** 无密码登录（F-1029）。 */
        LOGIN,
        /** 已登录用户的二次验证（F-1030）。 */
        SECOND_FACTOR
    }

    /**
     * 按 credential id 解析已存公钥的回调（实现由用例提供，桥接仓储，避免端口直接依赖仓储）。
     */
    @FunctionalInterface
    interface PublicKeyResolver {
        /**
         * @param credentialId 断言中携带的 credential id
         * @return 该凭据的公钥（base64），不存在返回 {@code null}
         */
        String resolve(CredentialId credentialId);
    }

    /**
     * 注册 options（begin 产物，可直接序列化下发前端）。
     *
     * @param optionsJson 结构化为 JSON 字符串的 CredentialCreationOptions
     */
    record RegistrationOptions(String optionsJson) {
    }

    /**
     * 断言 options（begin 产物，可直接序列化下发前端）。
     *
     * @param optionsJson 结构化为 JSON 字符串的 CredentialRequestOptions
     */
    record AssertionOptions(String optionsJson) {
    }

    /**
     * 注册验签结果（finish 产物，喂给聚合 register）。
     *
     * @param credentialId    解析出的 credential id
     * @param publicKey       解析出的公钥（base64）
     * @param attestationType attestation 类型（可空）
     * @param aaguid          AAGUID（可空）
     * @param signCount       初始签名计数器
     * @param flags           authenticator 行为标志
     * @param transports      transports（可空）
     * @param attachment      连接形态（可空）
     */
    record RegistrationResult(
            CredentialId credentialId,
            String publicKey,
            String attestationType,
            String aaguid,
            SignCount signCount,
            AuthenticatorFlags flags,
            String transports,
            String attachment) {
    }

    /**
     * 断言验签结果（finish 产物，喂给聚合 recordSuccessfulAssertion）。
     *
     * @param credentialId 断言对应的 credential id
     * @param signCount    新签名计数器
     * @param flags        本次断言的 authenticator 行为标志
     */
    record AssertionResult(
            CredentialId credentialId,
            SignCount signCount,
            AuthenticatorFlags flags) {
    }
}
