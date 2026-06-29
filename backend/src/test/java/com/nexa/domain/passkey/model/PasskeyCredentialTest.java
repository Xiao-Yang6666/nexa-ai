package com.nexa.domain.passkey.model;

import com.nexa.domain.passkey.exception.InvalidPasskeyCeremonyException;
import com.nexa.domain.passkey.vo.AuthenticatorFlags;
import com.nexa.domain.passkey.vo.CredentialId;
import com.nexa.domain.passkey.vo.SignCount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PasskeyCredential} 充血领域模型单测（纯 JUnit，不起 Spring/DB）。
 *
 * <p>覆盖 passkey 域核心领域逻辑（F-1028~1032）：注册工厂不变量、字段长度边界、签名计数器单调推进与
 * 克隆告警状态机、敏感字段不变量。按正常/边界/异常三类组织（backend-engineer §3.3）。</p>
 */
@DisplayName("PasskeyCredential 充血领域模型")
class PasskeyCredentialTest {

    private static final CredentialId CRED = CredentialId.of("cred-abc");
    private static final String PUBKEY = "BASE64_PUBLIC_KEY";

    private static PasskeyCredential newCredential(long initialSignCount) {
        return PasskeyCredential.register(
                42L, CRED, PUBKEY, "none", "aaguid-1",
                SignCount.of(initialSignCount), AuthenticatorFlags.userPresentOnly(),
                "internal", "platform");
    }

    @Nested
    @DisplayName("register 工厂")
    class Register {

        @Test
        @DisplayName("正常：合法入参创建凭据，id 未赋值、cloneWarning 初始为 false")
        void registerValid() {
            PasskeyCredential c = newCredential(0L);

            assertNull(c.id(), "未持久化新凭据 id 为 null");
            assertEquals(42L, c.userId());
            assertEquals(CRED, c.credentialId());
            assertEquals(PUBKEY, c.publicKey());
            assertEquals(0L, c.signCount().value());
            assertFalse(c.cloneWarning(), "注册时无克隆告警");
            assertEquals("platform", c.attachment());
        }

        @Test
        @DisplayName("边界：可选字段空白归一为 null，不报错")
        void registerBlankOptionalsBecomeNull() {
            PasskeyCredential c = PasskeyCredential.register(
                    1L, CRED, PUBKEY, "  ", "", SignCount.ZERO,
                    AuthenticatorFlags.userPresentOnly(), null, null);

            assertNull(c.attestationType());
            assertNull(c.aaguid());
            assertNull(c.transports());
            assertNull(c.attachment());
        }

        @Test
        @DisplayName("异常：userId 非正抛 InvalidPasskeyCeremonyException")
        void registerRejectsNonPositiveUserId() {
            assertThrows(InvalidPasskeyCeremonyException.class, () ->
                    PasskeyCredential.register(0L, CRED, PUBKEY, null, null, SignCount.ZERO,
                            AuthenticatorFlags.userPresentOnly(), null, null));
        }

        @Test
        @DisplayName("异常：空公钥抛 InvalidPasskeyCeremonyException")
        void registerRejectsBlankPublicKey() {
            assertThrows(InvalidPasskeyCeremonyException.class, () ->
                    PasskeyCredential.register(1L, CRED, "   ", null, null, SignCount.ZERO,
                            AuthenticatorFlags.userPresentOnly(), null, null));
        }

        @Test
        @DisplayName("异常：attachment 超长（>32）抛 InvalidPasskeyCeremonyException")
        void registerRejectsOverlongAttachment() {
            String tooLong = "x".repeat(33);
            assertThrows(InvalidPasskeyCeremonyException.class, () ->
                    PasskeyCredential.register(1L, CRED, PUBKEY, null, null, SignCount.ZERO,
                            AuthenticatorFlags.userPresentOnly(), null, tooLong));
        }
    }

    @Nested
    @DisplayName("recordSuccessfulAssertion 计数器推进 + 克隆检测")
    class RecordAssertion {

        @Test
        @DisplayName("正常：计数器递增，无克隆告警，标志被刷新")
        void incrementsCounterNoWarning() {
            PasskeyCredential c = newCredential(5L);
            AuthenticatorFlags verified = new AuthenticatorFlags(true, true, false, false);

            c.recordSuccessfulAssertion(SignCount.of(6L), verified);

            assertEquals(6L, c.signCount().value());
            assertFalse(c.cloneWarning(), "递增计数器不触发克隆告警");
            assertTrue(c.flags().userVerified(), "标志被刷新为本次断言的标志");
        }

        @Test
        @DisplayName("异常态：计数器回退（均非0且新值≤旧值）置克隆告警，但计数器仍推进")
        void counterRollbackTriggersCloneWarning() {
            PasskeyCredential c = newCredential(10L);

            c.recordSuccessfulAssertion(SignCount.of(8L), AuthenticatorFlags.userPresentOnly());

            assertTrue(c.cloneWarning(), "计数器回退应置克隆告警（WebAuthn 防克隆）");
            assertEquals(8L, c.signCount().value(), "无论是否告警，计数器都推进到 authenticator 最新值");
        }

        @Test
        @DisplayName("边界：authenticator 不实现计数器（0→0）不误报克隆")
        void zeroCountersDoNotWarn() {
            PasskeyCredential c = newCredential(0L);

            c.recordSuccessfulAssertion(SignCount.ZERO, AuthenticatorFlags.userPresentOnly());

            assertFalse(c.cloneWarning(), "计数器恒为 0 的 authenticator 不应误报克隆");
        }

        @Test
        @DisplayName("状态机：克隆告警一旦置真不回退（后续正常递增仍保留告警）")
        void cloneWarningStickyOnceSet() {
            PasskeyCredential c = newCredential(10L);
            c.recordSuccessfulAssertion(SignCount.of(8L), AuthenticatorFlags.userPresentOnly()); // 触发告警
            assertTrue(c.cloneWarning());

            c.recordSuccessfulAssertion(SignCount.of(9L), AuthenticatorFlags.userPresentOnly()); // 正常递增

            assertTrue(c.cloneWarning(), "一次可疑即长期标注，便于后续审计/重置");
        }
    }

    @Nested
    @DisplayName("rehydrate 重建工厂")
    class Rehydrate {

        @Test
        @DisplayName("正常：从已存数据重建聚合，状态完整还原")
        void rehydrateRestoresState() {
            PasskeyCredential c = PasskeyCredential.rehydrate(
                    7L, 42L, CRED, PUBKEY, "packed", "aaguid-x",
                    SignCount.of(99L), true,
                    new AuthenticatorFlags(true, true, true, true), "usb", "cross-platform");

            assertEquals(7L, c.id());
            assertEquals(99L, c.signCount().value());
            assertTrue(c.cloneWarning());
            assertTrue(c.flags().backupState());
            assertEquals("cross-platform", c.attachment());
        }

        @Test
        @DisplayName("重建不触发注册校验（容忍历史数据），不抛异常")
        void rehydrateSkipsRegistrationInvariants() {
            assertDoesNotThrow(() -> PasskeyCredential.rehydrate(
                    1L, 42L, CRED, PUBKEY, null, null,
                    SignCount.ZERO, false, AuthenticatorFlags.userPresentOnly(), null, null));
        }
    }
}
