package com.nexa.domain.account.model;

import com.nexa.domain.account.exception.InvalidCredentialException;
import com.nexa.domain.account.exception.OAuthBindingConflictException;
import com.nexa.domain.account.vo.OAuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link OAuthBinding} 充血领域模型单测（纯 JUnit，不起 Spring/DB）。
 *
 * <p>覆盖本切片新增/相关的领域逻辑（F-1025/1026/1027）：内建/自定义 provider 工厂的不变量校验、
 * 自定义 provider 的 {@code providerRefId} 装载、解绑归属护栏 {@link OAuthBinding#ensureOwnedBy(long)}、
 * 重建工厂 {@link OAuthBinding#rehydrate}。按正常/边界/异常三类组织（backend-engineer §3.3）。</p>
 */
@DisplayName("OAuthBinding 充血领域模型")
class OAuthBindingTest {

    @Nested
    @DisplayName("create 内建 provider 绑定工厂")
    class CreateBuiltin {

        @Test
        @DisplayName("正常：合法入参创建内建绑定，providerRefId 为 null、id 未赋值")
        void createValid() {
            OAuthBinding b = OAuthBinding.create(42L, OAuthProvider.GITHUB, "gh-123");

            assertEquals(42L, b.userId());
            assertEquals(OAuthProvider.GITHUB, b.provider());
            assertEquals("gh-123", b.providerUserId());
            assertNull(b.providerRefId(), "内建 provider 绑定不带自定义 provider 整数引用");
            assertNull(b.id(), "未持久化的新绑定 id 为 null");
        }

        @Test
        @DisplayName("正常：providerUserId 前后空白被 trim")
        void trimsProviderUserId() {
            OAuthBinding b = OAuthBinding.create(1L, OAuthProvider.DISCORD, "  dc-9  ");
            assertEquals("dc-9", b.providerUserId());
        }

        @Test
        @DisplayName("异常：userId 非正抛 InvalidCredentialException")
        void rejectsNonPositiveUserId() {
            assertThrows(InvalidCredentialException.class,
                    () -> OAuthBinding.create(0L, OAuthProvider.GITHUB, "x"));
        }

        @Test
        @DisplayName("异常：providerUserId 空白抛 InvalidCredentialException")
        void rejectsBlankProviderUserId() {
            assertThrows(InvalidCredentialException.class,
                    () -> OAuthBinding.create(1L, OAuthProvider.GITHUB, "   "));
        }

        @Test
        @DisplayName("边界：providerUserId 超长抛 InvalidCredentialException")
        void rejectsTooLongProviderUserId() {
            String tooLong = "a".repeat(OAuthBinding.PROVIDER_USER_ID_MAX_LENGTH + 1);
            assertThrows(InvalidCredentialException.class,
                    () -> OAuthBinding.create(1L, OAuthProvider.GITHUB, tooLong));
        }

        @Test
        @DisplayName("边界：providerUserId 恰好等于上限长度可创建")
        void acceptsMaxLengthProviderUserId() {
            String atLimit = "a".repeat(OAuthBinding.PROVIDER_USER_ID_MAX_LENGTH);
            OAuthBinding b = OAuthBinding.create(1L, OAuthProvider.OIDC, atLimit);
            assertEquals(atLimit, b.providerUserId());
        }
    }

    @Nested
    @DisplayName("createForCustomProvider 自定义 provider 绑定工厂")
    class CreateCustom {

        @Test
        @DisplayName("正常：合法入参创建自定义绑定，providerRefId 装载整数 id")
        void createValid() {
            OAuthBinding b = OAuthBinding.createForCustomProvider(7L, 99L, "ext-user-1");

            assertEquals(7L, b.userId());
            assertEquals(99L, b.providerRefId(), "自定义 provider 绑定携带其整数主键引用");
            assertEquals("ext-user-1", b.providerUserId());
            assertNull(b.id());
        }

        @Test
        @DisplayName("异常：providerRefId 非正抛 InvalidCredentialException")
        void rejectsNonPositiveProviderRefId() {
            assertThrows(InvalidCredentialException.class,
                    () -> OAuthBinding.createForCustomProvider(1L, 0L, "x"));
        }

        @Test
        @DisplayName("异常：userId 非正抛 InvalidCredentialException")
        void rejectsNonPositiveUserId() {
            assertThrows(InvalidCredentialException.class,
                    () -> OAuthBinding.createForCustomProvider(0L, 5L, "x"));
        }

        @Test
        @DisplayName("异常：providerUserId 空白抛 InvalidCredentialException")
        void rejectsBlankProviderUserId() {
            assertThrows(InvalidCredentialException.class,
                    () -> OAuthBinding.createForCustomProvider(1L, 5L, ""));
        }
    }

    @Nested
    @DisplayName("ensureOwnedBy 解绑归属护栏")
    class EnsureOwnedBy {

        @Test
        @DisplayName("正常：归属一致时通过（幂等，不抛）")
        void passesWhenOwned() {
            OAuthBinding b = OAuthBinding.create(100L, OAuthProvider.GITHUB, "gh-1");
            assertDoesNotThrow(() -> b.ensureOwnedBy(100L));
        }

        @Test
        @DisplayName("异常：归属另一用户时抛 OAuthBindingConflictException（不能解他人绑定）")
        void rejectsWhenOwnedByAnother() {
            OAuthBinding b = OAuthBinding.create(100L, OAuthProvider.GITHUB, "gh-1");
            assertThrows(OAuthBindingConflictException.class, () -> b.ensureOwnedBy(200L));
        }
    }

    @Nested
    @DisplayName("rehydrate 持久化重建工厂")
    class Rehydrate {

        @Test
        @DisplayName("正常：内建 provider 重建装回字段，providerRefId 为 null")
        void rehydrateBuiltin() {
            Instant now = Instant.now();
            OAuthBinding b = OAuthBinding.rehydrate(5L, 100L, OAuthProvider.LINUXDO, "ld-7", now);

            assertEquals(5L, b.id());
            assertEquals(100L, b.userId());
            assertEquals(OAuthProvider.LINUXDO, b.provider());
            assertEquals("ld-7", b.providerUserId());
            assertNull(b.providerRefId());
            assertEquals(now, b.createdAt());
        }

        @Test
        @DisplayName("正常：自定义 provider 重建保留 providerRefId 整数引用")
        void rehydrateCustom() {
            Instant now = Instant.now();
            OAuthBinding b = OAuthBinding.rehydrate(
                    8L, 100L, OAuthProvider.OIDC, "ext-9", 55L, now);

            assertEquals(8L, b.id());
            assertEquals(55L, b.providerRefId());
            assertEquals("ext-9", b.providerUserId());
        }
    }

    @Nested
    @DisplayName("assignId 持久化后回填主键")
    class AssignId {

        @Test
        @DisplayName("正常：保存后回填 id 可被读取")
        void assignsId() {
            OAuthBinding b = OAuthBinding.create(1L, OAuthProvider.GITHUB, "gh-1");
            assertNull(b.id());
            b.assignId(123L);
            assertEquals(123L, b.id());
        }
    }
}
