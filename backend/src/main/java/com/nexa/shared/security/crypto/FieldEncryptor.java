package com.nexa.shared.security.crypto;

import com.nexa.shared.security.exception.FieldEncryptionException;

/**
 * 敏感字段加解密端口（领域接口，repository/port 抽象）。
 *
 * <p>DDD §2.3：domain 层<b>只定义接口</b>，具体对称加密算法实现（AES-256-GCM）落在
 * {@code infrastructure}（{@code AesGcmFieldEncryptor}）。应用/领域层只依赖本接口，可替换、可 mock 单测，
 * 不依赖任何 JCE/框架类型。</p>
 *
 * <p>用途：库内可逆存储的敏感字段（渠道上游凭据/第三方 token 等）的加密落库与读取解密；
 * <b>不</b>用于用户密码（密码不可逆，走 BCrypt）。</p>
 */
public interface FieldEncryptor {

    /**
     * 加密明文为密文信封。
     *
     * @param plainText 待加密明文（非 null；空串也会被加密以保持可逆对称性）
     * @return 密文信封值对象（含算法版本 + 随机 IV，相同明文每次密文不同）
     * @throws FieldEncryptionException 当密钥不可用或底层加密失败
     */
    EncryptedValue encrypt(String plainText);

    /**
     * 解密密文信封还原明文。
     *
     * @param encrypted 密文信封值对象
     * @return 还原后的明文
     * @throws FieldEncryptionException 当密钥不匹配、密文被篡改（GCM 认证失败）或结构损坏
     */
    String decrypt(EncryptedValue encrypted);
}
