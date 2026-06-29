package com.nexa.common.security.domain.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EncryptedValue} 加密字段值对象单测（纯 JUnit，不起 Spring/DB）。
 *
 * <p>覆盖敏感数据加密功能里值对象的不变量与安全语义：非空密文构造、空值拒绝、按值相等、以及
 * {@link EncryptedValue#toString()} 必须脱敏不回显密文（避免日志侧信道泄露——本切片敏感数据加密的关键
 * 安全规则）。按正常/边界/异常三类组织（backend-engineer §3.3）。</p>
 */
@DisplayName("EncryptedValue 加密字段值对象")
class EncryptedValueTest {

    @Test
    @DisplayName("正常：从密文信封字符串构造并取回")
    void buildFromCipherText() {
        EncryptedValue v = EncryptedValue.ofCipherText("AQIDBASE64ENVELOPE==");
        assertEquals("AQIDBASE64ENVELOPE==", v.cipherText());
    }

    @Test
    @DisplayName("异常：null 密文拒绝")
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> EncryptedValue.ofCipherText(null));
    }

    @Test
    @DisplayName("异常：空/空白密文拒绝")
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> EncryptedValue.ofCipherText(""));
        assertThrows(IllegalArgumentException.class, () -> EncryptedValue.ofCipherText("   "));
    }

    @Test
    @DisplayName("值语义：相同密文相等且 hashCode 一致，不同密文不相等")
    void valueSemantics() {
        EncryptedValue a = EncryptedValue.ofCipherText("CIPHER-A");
        EncryptedValue b = EncryptedValue.ofCipherText("CIPHER-A");
        EncryptedValue c = EncryptedValue.ofCipherText("CIPHER-B");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    @DisplayName("安全：toString 脱敏，绝不回显密文")
    void toStringMasksCipher() {
        String secret = "SUPER-SECRET-CIPHER-ENVELOPE";
        EncryptedValue v = EncryptedValue.ofCipherText(secret);
        String s = v.toString();
        assertFalse(s.contains(secret), "toString 不得包含密文，防日志侧信道泄露");
        assertTrue(s.contains("***"), "toString 应给脱敏占位");
    }
}
