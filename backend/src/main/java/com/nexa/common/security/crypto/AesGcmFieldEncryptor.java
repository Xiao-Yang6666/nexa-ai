package com.nexa.common.security.crypto;

import com.nexa.common.security.crypto.EncryptedValue;
import com.nexa.common.security.crypto.FieldEncryptor;
import com.nexa.common.security.exception.FieldEncryptionException;
import com.nexa.common.security.config.SecurityProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 敏感字段加密端口 {@link FieldEncryptor} 的 AES-256-GCM 实现（基础设施层）。
 *
 * <p>实现 domain 定义的接口（DDD §2.3），把 JCE 这一基础设施细节关在 infra 层，domain/application
 * 只见 {@link FieldEncryptor} 抽象。算法选型 AES-256-GCM：认证加密（AEAD），同时保证机密性与完整性，
 * 密文被篡改解密时 GCM 认证标签校验失败抛错而非静默返回错误明文。</p>
 *
 * <p>密文信封格式（Base64 编码整体）：{@code [1B 版本号=0x01][12B 随机 IV][密文+16B GCM tag]}。
 * IV 每次加密随机生成（{@link SecureRandom}），保证相同明文密文不同，杜绝 ECB 式模式泄露。</p>
 *
 * <p>密钥来源：{@link SecurityProperties.Encryption#getKey()}（Base64 的 32 字节），生产经环境变量注入，
 * 绝不硬编码（backend-engineer §3.4）。密钥缺失/长度非法时构造期即抛错，避免运行期才暴露。</p>
 */
@Component
public class AesGcmFieldEncryptor implements FieldEncryptor {

    /** 密文信封版本号，便于未来算法/密钥轮换时区分格式。 */
    private static final byte VERSION = 0x01;

    /** GCM 推荐 IV 长度 12 字节（96 位）。 */
    private static final int IV_LENGTH = 12;

    /** GCM 认证标签长度 128 位。 */
    private static final int GCM_TAG_BITS = 128;

    /** AES-256 要求密钥 32 字节。 */
    private static final int KEY_LENGTH_BYTES = 32;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @param properties 安全配置（读取 Base64 主密钥）
     * @throws FieldEncryptionException 当密钥缺失或长度不是 AES-256 要求的 32 字节
     */
    public AesGcmFieldEncryptor(SecurityProperties properties) {
        String base64Key = properties.getEncryption().getKey();
        if (base64Key == null || base64Key.isBlank()) {
            // 早失败：缺密钥则该 bean 创建失败，应用启动即暴露配置缺失，而非运行期到加密点才炸。
            throw new FieldEncryptionException(
                    "security.encryption.key is not configured (Base64 32-byte AES key required)");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new FieldEncryptionException("security.encryption.key is not valid Base64", e);
        }
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new FieldEncryptionException(
                    "security.encryption.key must decode to " + KEY_LENGTH_BYTES
                            + " bytes for AES-256, got " + keyBytes.length);
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    /** {@inheritDoc} */
    @Override
    public EncryptedValue encrypt(String plainText) {
        if (plainText == null) {
            throw new FieldEncryptionException("plainText must not be null");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 信封拼装：版本 + IV + 密文(含 tag)，整体 Base64。
            byte[] envelope = new byte[1 + IV_LENGTH + cipherBytes.length];
            envelope[0] = VERSION;
            System.arraycopy(iv, 0, envelope, 1, IV_LENGTH);
            System.arraycopy(cipherBytes, 0, envelope, 1 + IV_LENGTH, cipherBytes.length);

            return EncryptedValue.ofCipherText(Base64.getEncoder().encodeToString(envelope));
        } catch (Exception e) {
            // wrap 带上下文，不外泄明文/密钥（backend-engineer §3.2）。
            throw new FieldEncryptionException("encrypt failed", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String decrypt(EncryptedValue encrypted) {
        if (encrypted == null) {
            throw new FieldEncryptionException("encrypted value must not be null");
        }
        byte[] envelope;
        try {
            envelope = Base64.getDecoder().decode(encrypted.cipherText());
        } catch (IllegalArgumentException e) {
            throw new FieldEncryptionException("decrypt failed: cipher text is not valid Base64", e);
        }
        if (envelope.length < 1 + IV_LENGTH + (GCM_TAG_BITS / 8)) {
            throw new FieldEncryptionException("decrypt failed: cipher envelope too short / corrupted");
        }
        if (envelope[0] != VERSION) {
            throw new FieldEncryptionException(
                    "decrypt failed: unsupported cipher version " + envelope[0]);
        }
        try {
            byte[] iv = Arrays.copyOfRange(envelope, 1, 1 + IV_LENGTH);
            byte[] cipherBytes = Arrays.copyOfRange(envelope, 1 + IV_LENGTH, envelope.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // GCM 认证失败（密文被篡改/密钥不符）落到这里——属完整性问题，向上传不静默。
            throw new FieldEncryptionException("decrypt failed: authentication or key mismatch", e);
        }
    }
}
