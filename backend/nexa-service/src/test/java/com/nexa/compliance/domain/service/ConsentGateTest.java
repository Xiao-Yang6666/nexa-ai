package com.nexa.compliance.domain.service;

import com.nexa.compliance.domain.exception.ConsentRequiredException;
import com.nexa.compliance.domain.vo.Consent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConsentGate} + {@link Consent} 单测（纯 JUnit）——F-5021 同意闸门，DC-010。
 *
 * <p>验证「未同意 / 同意版本过期 → 拒绝调用；同意当前版本 → 放行」（验收「未同意协议拒绝调用」）。</p>
 */
@DisplayName("同意闸门")
class ConsentGateTest {

    private static final String CURRENT = "2026-06-01";

    @Test
    @DisplayName("从未同意 → 拒绝")
    void neverConsented() {
        assertFalse(ConsentGate.isSatisfied(Consent.none(), CURRENT));
        assertFalse(ConsentGate.isSatisfied(null, CURRENT));
        assertThrows(ConsentRequiredException.class,
                () -> ConsentGate.requireConsent(Consent.none(), CURRENT));
        assertThrows(ConsentRequiredException.class,
                () -> ConsentGate.requireConsent(null, CURRENT));
    }

    @Test
    @DisplayName("同意了当前版本 → 放行")
    void consentedCurrent() {
        Consent c = Consent.accepted(CURRENT, 1_700_000_000L);
        assertTrue(ConsentGate.isSatisfied(c, CURRENT));
        assertDoesNotThrow(() -> ConsentGate.requireConsent(c, CURRENT));
    }

    @Test
    @DisplayName("同意了旧版本（条款已升级）→ 拒绝，需重新同意")
    void consentedStaleVersion() {
        Consent old = Consent.accepted("2026-01-01", 1_700_000_000L);
        assertFalse(ConsentGate.isSatisfied(old, CURRENT));
        assertThrows(ConsentRequiredException.class,
                () -> ConsentGate.requireConsent(old, CURRENT));
    }

    @Test
    @DisplayName("Consent.accepted 入参校验：版本空白/时刻非正抛异常")
    void consentValidation() {
        assertThrows(IllegalArgumentException.class, () -> Consent.accepted("  ", 1L));
        assertThrows(IllegalArgumentException.class, () -> Consent.accepted("v1", 0L));
    }
}
