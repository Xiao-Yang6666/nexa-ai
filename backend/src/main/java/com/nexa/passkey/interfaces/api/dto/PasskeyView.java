package com.nexa.passkey.interfaces.api.dto;

import com.nexa.passkey.domain.model.PasskeyCredential;

/**
 * Passkey 客户视图 DTO（对齐 openapi.yaml {@code PasskeyView}，本人状态查询返回，F-1031）。
 *
 * <p><b>客户视图零敏感泄露（产品铁律）</b>：本 DTO <b>绝不</b>包含 {@code public_key} 全文、
 * {@code transports} 原文等可被用于伪造/分析的字段，也不含 clone_warning 这类内部审计标记。仅下发
 * openapi 约定的本人可见字段（id / credential_id / attestation_type / sign_count / attachment）。</p>
 *
 * @param id              凭据主键
 * @param credentialId    WebAuthn credential id（公开标识，非密钥）
 * @param attestationType attestation 类型（可空）
 * @param signCount       签名计数器
 * @param attachment      authenticator 连接形态（可空）
 */
public record PasskeyView(
        Long id,
        String credentialId,
        String attestationType,
        long signCount,
        String attachment) {

    /**
     * 从凭据聚合投影为客户视图 DTO。
     *
     * <p>显式逐字段映射而非反射拷贝——确保新增聚合字段（尤其 publicKey 等敏感字段）不会「默认泄露」到
     * 客户视图；publicKey 在此处<b>根本不读取</b>，从源头杜绝下发。</p>
     *
     * @param credential passkey 凭据聚合
     * @return 客户视图 DTO（无任何敏感字段）
     */
    public static PasskeyView from(PasskeyCredential credential) {
        return new PasskeyView(
                credential.id(),
                credential.credentialId().value(),
                credential.attestationType(),
                credential.signCount().value(),
                credential.attachment());
    }
}
