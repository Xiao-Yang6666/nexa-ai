package com.nexa.shared.security.crypto;

import com.nexa.shared.security.crypto.EncryptedValue;
import com.nexa.shared.security.exception.FieldEncryptionException;
import com.nexa.shared.security.config.SecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AesGcmFieldEncryptor} 单测（纯 JUnit，不起 Spring——直接 new SecurityProperties 注入密钥）。
 *
 * <p>本类虽属 infrastructure 层，但 AES-256-GCM 加解密是敏感数据加密功能的核心安全逻辑，且不依赖
 * 任何 Spring/DB 上下文（仅 JCE），故必测（backend-engineer §3.3）。覆盖：构造期密钥校验（缺失/非法
 * Base64/长度错）、加解密往返一致、相同明文密文不同（随机 IV）、密文被篡改时 GCM 认证失败抛错而非
 * 静默返回错误明文（完整性保证）。按正常/边界/异常三类组织。</p>
 */
@DisplayName("AesGcmFieldEncryptor AES-256-GCM 字段加密")
class AesGcmFieldEncryptorTest {

    /** 测试用 32 字节（AES-256）全零密钥的 Base64，仅供测试，绝非生产密钥。 */
    private static final String VALID_KEY_B64 = Base64.getEncoder().encodeToString(new byte[32]);

    private static AesGcmFieldEncryptor encryptorWithKey(String base64Key) {
        SecurityProperties props = new SecurityProperties();
        props.getEncryption().setKey(base64Key);
        return new AesGcmFieldEncryptor(props);
    }

    @Test
    @DisplayName("正常：加解密往返还原原文")
    void roundTrip() {
        AesGcmFieldEncryptor enc = encryptorWithKey(VALID_KEY_B64);
        String plain = "sk-upstream-api-key-1234567890";
        EncryptedValue cipher = enc.encrypt(plain);
        assertEquals(plain, enc.decrypt(cipher));
    }

    @Test
    @DisplayName("正常：空串也可加解密（保持可逆对称性）")
    void roundTripEmpty() {
        AesGcmFieldEncryptor enc = encryptorWithKey(VALID_KEY_B64);
        EncryptedValue cipher = enc.encrypt("");
        assertEquals("", enc.decrypt(cipher));
    }

    @Test
    @DisplayName("安全：相同明文两次加密密文不同（随机 IV，杜绝模式泄露）")
    void sameInputDifferentCipher() {
        AesGcmFieldEncryptor enc = encryptorWithKey(VALID_KEY_B64);
        String plain = "same-plaintext";
        assertNotEquals(enc.encrypt(plain).cipherText(), enc.encrypt(plain).cipherText());
    }

    @Test
    @DisplayName("安全：密文被篡改时解密抛错（GCM 认证失败），不静默返回错误明文")
    void tamperedCipherFailsAuthentication() {
        AesGcmFieldEncryptor enc = encryptorWithKey(VALID_KEY_B64);
        EncryptedValue good = enc.encrypt("integrity-protected");

        // 翻转密文 Base64 解码后中间一个字节，重新编码模拟篡改。
        byte[] raw = Base64.getDecoder().decode(good.cipherText());
        raw[raw.length / 2] ^= 0x01;
        EncryptedValue tampered = EncryptedValue.ofCipherText(Base64.getEncoder().encodeToString(raw));

        assertThrows(FieldEncryptionException.class, () -> enc.decrypt(tampered));
    }

    @Test
    @DisplayName("异常：用不同密钥解密失败（密钥不匹配 → 认证失败）")
    void wrongKeyFailsDecryption() {
        AesGcmFieldEncryptor enc1 = encryptorWithKey(VALID_KEY_B64);
        byte[] otherKey = new byte[32];
        otherKey[0] = 0x7F;
        AesGcmFieldEncryptor enc2 = encryptorWithKey(Base64.getEncoder().encodeToString(otherKey));

        EncryptedValue cipher = enc1.encrypt("secret");
        assertThrows(FieldEncryptionException.class, () -> enc2.decrypt(cipher));
    }

    @Test
    @DisplayName("异常：损坏/过短的密文信封解密抛错")
    void corruptedEnvelopeFails() {
        AesGcmFieldEncryptor enc = encryptorWithKey(VALID_KEY_B64);
        EncryptedValue tooShort = EncryptedValue.ofCipherText(
                Base64.getEncoder().encodeToString(new byte[]{0x01, 0x02}));
        assertThrows(FieldEncryptionException.class, () -> enc.decrypt(tooShort));
    }

    @Test
    @DisplayName("异常：非 Base64 密文解密抛错")
    void nonBase64Fails() {
        AesGcmFieldEncryptor enc = encryptorWithKey(VALID_KEY_B64);
        EncryptedValue bad = EncryptedValue.ofCipherText("not!!!base64@@@");
        assertThrows(FieldEncryptionException.class, () -> enc.decrypt(bad));
    }

    @Test
    @DisplayName("异常：构造期密钥缺失早失败")
    void missingKeyFailsFast() {
        assertThrows(FieldEncryptionException.class, () -> encryptorWithKey(""));
    }

    @Test
    @DisplayName("异常：构造期密钥非法 Base64 早失败")
    void invalidBase64KeyFailsFast() {
        assertThrows(FieldEncryptionException.class, () -> encryptorWithKey("###not-base64###"));
    }

    @Test
    @DisplayName("异常：构造期密钥长度非 32 字节（非 AES-256）早失败")
    void wrongKeyLengthFailsFast() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]); // AES-128 长度
        assertThrows(FieldEncryptionException.class, () -> encryptorWithKey(shortKey));
    }
}
