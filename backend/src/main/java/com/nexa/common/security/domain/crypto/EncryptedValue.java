package com.nexa.common.security.domain.crypto;

import java.util.Objects;

/**
 * 加密字段值对象（敏感数据加密）。
 *
 * <p>不可变、按值相等。封装一段敏感明文经对称加密后的<b>密文信封</b>（Base64 编码字符串，内部含算法
 * 版本前缀 + IV + 密文 + GCM 认证标签）。用于库内可逆存储的敏感字段（如渠道上游 API Key、第三方
 * OAuth token、SMTP 凭据等 DB-SCHEMA 标注的敏感语义列），区别于<b>不可逆</b>的密码哈希（密码走
 * BCrypt，永不解密）。</p>
 *
 * <p>领域规则：本 VO 只持有密文，<b>绝不</b>持有明文，从根上杜绝明文随对象在内存/日志中扩散；
 * 加解密由 {@link FieldEncryptor} 端口在需要时显式执行。{@link #toString()} 不回显密文，避免日志泄露。</p>
 *
 * <p>设计依据：backend-engineer §2.2 充血/值对象、§3.1 注释标出处；防注入与加密见
 * 本切片 SECURITY-NOTES。</p>
 */
public final class EncryptedValue {

    private final String cipherText;

    private EncryptedValue(String cipherText) {
        this.cipherText = cipherText;
    }

    /**
     * 从已加密的密文信封字符串重建值对象（用于从库读出后承载，不做加解密）。
     *
     * @param cipherText 非空密文信封（Base64，含算法版本前缀）
     * @return 加密值对象
     * @throws IllegalArgumentException 当密文为空
     */
    public static EncryptedValue ofCipherText(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            throw new IllegalArgumentException("cipherText must not be blank");
        }
        return new EncryptedValue(cipherText);
    }

    /** @return 密文信封字符串（落库即此值） */
    public String cipherText() {
        return cipherText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EncryptedValue other)) {
            return false;
        }
        return cipherText.equals(other.cipherText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cipherText);
    }

    /** @return 脱敏表示，绝不回显密文（避免日志/异常侧信道泄露） */
    @Override
    public String toString() {
        return "EncryptedValue[***]";
    }
}
