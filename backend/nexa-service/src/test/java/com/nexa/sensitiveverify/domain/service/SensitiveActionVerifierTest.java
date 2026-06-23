package com.nexa.sensitiveverify.domain.service;

import com.nexa.sensitiveverify.domain.exception.InvalidVerificationRequestException;
import com.nexa.sensitiveverify.domain.exception.SensitiveActionVerificationFailedException;
import com.nexa.sensitiveverify.domain.vo.VerificationMethod;
import com.nexa.sensitiveverify.domain.vo.VerificationOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SensitiveActionVerifier} 领域服务单测（纯 JUnit，零 Spring）。
 *
 * <p>覆盖 F-1038 核心裁决规则「任一通过即放行 / 全不通过即 403 / 无因子即 400」，按正常/边界/异常三类组织
 * （backend-engineer §3.3）。领域服务无框架依赖，可直接静态调用断言。</p>
 */
@DisplayName("SensitiveActionVerifier 二次验证裁决（F-1038）")
class SensitiveActionVerifierTest {

    // ---------- requireAnyPassed：正常（任一通过即放行） ----------

    @Test
    @DisplayName("单因子密码通过 → 放行（不抛异常）")
    void singlePasswordPassedAllows() {
        assertDoesNotThrow(() -> SensitiveActionVerifier.requireAnyPassed(
                List.of(VerificationOutcome.passed(VerificationMethod.PASSWORD))));
    }

    @Test
    @DisplayName("多因子中只要任一通过即放行（密码未过但 passkey 过）")
    void anyOnePassedAllows() {
        assertDoesNotThrow(() -> SensitiveActionVerifier.requireAnyPassed(List.of(
                VerificationOutcome.failed(VerificationMethod.PASSWORD),
                VerificationOutcome.passed(VerificationMethod.PASSKEY))));
    }

    @Test
    @DisplayName("TOTP / passkey 单因子通过 → 放行")
    void totpOrPasskeySinglePassedAllows() {
        assertDoesNotThrow(() -> SensitiveActionVerifier.requireAnyPassed(
                List.of(VerificationOutcome.passed(VerificationMethod.TOTP))));
        assertDoesNotThrow(() -> SensitiveActionVerifier.requireAnyPassed(
                List.of(VerificationOutcome.passed(VerificationMethod.PASSKEY))));
    }

    // ---------- requireAnyPassed：异常（全不通过 → 403） ----------

    @Test
    @DisplayName("单因子未通过 → 抛验证失败（403 语义）")
    void singleFailedRejects() {
        assertThrows(SensitiveActionVerificationFailedException.class,
                () -> SensitiveActionVerifier.requireAnyPassed(
                        List.of(VerificationOutcome.failed(VerificationMethod.PASSWORD))));
    }

    @Test
    @DisplayName("多因子全不通过 → 抛验证失败（403 语义）")
    void allFailedRejects() {
        assertThrows(SensitiveActionVerificationFailedException.class,
                () -> SensitiveActionVerifier.requireAnyPassed(List.of(
                        VerificationOutcome.failed(VerificationMethod.PASSWORD),
                        VerificationOutcome.failed(VerificationMethod.TOTP),
                        VerificationOutcome.failed(VerificationMethod.PASSKEY))));
    }

    @Test
    @DisplayName("验证失败异常携带稳定错误码 SENSITIVE_VERIFY_FAILED")
    void failedExceptionCarriesStableCode() {
        SensitiveActionVerificationFailedException ex = assertThrows(
                SensitiveActionVerificationFailedException.class,
                () -> SensitiveActionVerifier.requireAnyPassed(
                        List.of(VerificationOutcome.failed(VerificationMethod.TOTP))));
        assertTrue(SensitiveActionVerificationFailedException.CODE.equals(ex.code()));
    }

    // ---------- requireAnyPassed：边界（无因子 → 400） ----------

    @Test
    @DisplayName("空因子集合 → 抛请求非法（400 语义，区别于验证失败）")
    void emptyOutcomesIsBadRequest() {
        assertThrows(InvalidVerificationRequestException.class,
                () -> SensitiveActionVerifier.requireAnyPassed(List.of()));
    }

    @Test
    @DisplayName("null 因子集合 → NPE（防御式入参校验）")
    void nullOutcomesThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> SensitiveActionVerifier.requireAnyPassed(null));
    }

    // ---------- anyPassed：非抛出布尔版本 ----------

    @Test
    @DisplayName("anyPassed：任一通过返回 true")
    void anyPassedReturnsTrue() {
        assertTrue(SensitiveActionVerifier.anyPassed(List.of(
                VerificationOutcome.failed(VerificationMethod.PASSWORD),
                VerificationOutcome.passed(VerificationMethod.TOTP))));
    }

    @Test
    @DisplayName("anyPassed：全不通过或空集合均返回 false")
    void anyPassedReturnsFalse() {
        assertFalse(SensitiveActionVerifier.anyPassed(List.of(
                VerificationOutcome.failed(VerificationMethod.PASSWORD))));
        assertFalse(SensitiveActionVerifier.anyPassed(List.of()));
    }

    // ---------- VerificationOutcome 值对象不变量 ----------

    @Test
    @DisplayName("VerificationOutcome：method 为 null → NPE")
    void outcomeRejectsNullMethod() {
        assertThrows(NullPointerException.class, () -> new VerificationOutcome(null, true));
    }
}
