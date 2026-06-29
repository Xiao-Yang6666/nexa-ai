package com.nexa.domain.passkey.vo;

import com.nexa.domain.passkey.exception.InvalidPasskeyCeremonyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SignCount} / {@link CredentialId} 值对象单测（纯 JUnit）。
 *
 * <p>覆盖签名计数器克隆检测规则（WebAuthn L2 §6.1.1）与凭据标识不变量。按正常/边界/异常组织
 * （backend-engineer §3.3）。</p>
 */
@DisplayName("Passkey 值对象")
class SignCountTest {

    @Test
    @DisplayName("SignCount：均非0且新值≤旧值 → 克隆告警")
    void cloneWarningWhenRollback() {
        assertTrue(SignCount.of(10).isCloneWarning(SignCount.of(10)), "相等也视为回退（应严格递增）");
        assertTrue(SignCount.of(10).isCloneWarning(SignCount.of(5)));
    }

    @Test
    @DisplayName("SignCount：严格递增 → 无告警")
    void noWarningWhenIncrement() {
        assertFalse(SignCount.of(10).isCloneWarning(SignCount.of(11)));
    }

    @Test
    @DisplayName("SignCount：任一为0（不实现计数器）→ 不误报")
    void noWarningWhenZero() {
        assertFalse(SignCount.ZERO.isCloneWarning(SignCount.of(5)));
        assertFalse(SignCount.of(5).isCloneWarning(SignCount.ZERO));
    }

    @Test
    @DisplayName("SignCount：负值抛异常")
    void rejectsNegative() {
        assertThrows(InvalidPasskeyCeremonyException.class, () -> SignCount.of(-1));
    }

    @Test
    @DisplayName("CredentialId：trim 后非空合法")
    void credentialIdTrims() {
        assertEquals("abc", CredentialId.of("  abc  ").value());
    }

    @Test
    @DisplayName("CredentialId：空白抛异常")
    void credentialIdRejectsBlank() {
        assertThrows(InvalidPasskeyCeremonyException.class, () -> CredentialId.of("  "));
    }

    @Test
    @DisplayName("CredentialId：超长（>512）抛异常")
    void credentialIdRejectsOverlong() {
        assertThrows(InvalidPasskeyCeremonyException.class, () -> CredentialId.of("x".repeat(513)));
    }

    @Test
    @DisplayName("CredentialId：按值相等")
    void credentialIdValueEquality() {
        assertEquals(CredentialId.of("same"), CredentialId.of("same"));
    }
}
