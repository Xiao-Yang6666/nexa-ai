package com.nexa.shared.security.domain.rbac;

import com.nexa.shared.security.domain.exception.AccessDeniedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RBAC 角色 / 鉴权级别 / 认证主体护栏单测（纯 JUnit，不起 Spring/DB）。
 *
 * <p>覆盖三级鉴权（F-5031）与 self-scope 越权防护（F-5032）的核心领域逻辑，按正常/边界/异常三类组织
 * （backend-engineer §3.3）：角色层级比较、级别满足判定、{@code requireAtLeast}/{@code requireSelfScopeOrAdmin}/
 * {@code requireHigherThan} 护栏的放行与拒绝。这是鉴权基础设施的核心逻辑，必测。</p>
 */
@DisplayName("RBAC 角色 / 级别 / 认证主体护栏")
class AuthenticatedActorTest {

    @Nested
    @DisplayName("ActorRole 角色层级")
    class RoleHierarchy {

        @Test
        @DisplayName("数值大小即权限高低：root > admin > common")
        void hierarchy() {
            assertTrue(ActorRole.ROOT.isHigherThan(ActorRole.ADMIN));
            assertTrue(ActorRole.ADMIN.isHigherThan(ActorRole.COMMON));
            assertTrue(ActorRole.ROOT.isHigherThan(ActorRole.COMMON));
            assertFalse(ActorRole.ADMIN.isHigherThan(ActorRole.ADMIN));
            assertFalse(ActorRole.COMMON.isHigherThan(ActorRole.ADMIN));
        }

        @Test
        @DisplayName("satisfies：高角色满足低门槛，同级满足，低角色不满足高门槛")
        void satisfies() {
            assertTrue(ActorRole.ADMIN.satisfies(ActorRole.ADMIN));
            assertTrue(ActorRole.ROOT.satisfies(ActorRole.ADMIN));
            assertTrue(ActorRole.ADMIN.satisfies(ActorRole.COMMON));
            assertFalse(ActorRole.COMMON.satisfies(ActorRole.ADMIN));
            assertFalse(ActorRole.ADMIN.satisfies(ActorRole.ROOT));
        }

        @Test
        @DisplayName("fromCode：已知编码还原，未知编码拒绝（脏数据不静默放行）")
        void fromCode() {
            assertEquals(ActorRole.COMMON, ActorRole.fromCode(1));
            assertEquals(ActorRole.ADMIN, ActorRole.fromCode(10));
            assertEquals(ActorRole.ROOT, ActorRole.fromCode(100));
            assertThrows(IllegalArgumentException.class, () -> ActorRole.fromCode(999));
            assertThrows(IllegalArgumentException.class, () -> ActorRole.fromCode(0));
        }
    }

    @Nested
    @DisplayName("AuthLevel 鉴权级别")
    class Level {

        @Test
        @DisplayName("级别映射到最低要求角色")
        void minimumRole() {
            assertEquals(ActorRole.COMMON, AuthLevel.USER.minimumRole());
            assertEquals(ActorRole.ADMIN, AuthLevel.ADMIN.minimumRole());
            assertEquals(ActorRole.ROOT, AuthLevel.ROOT.minimumRole());
        }

        @Test
        @DisplayName("isSatisfiedBy：admin 满足 USER/ADMIN 不满足 ROOT")
        void isSatisfiedBy() {
            assertTrue(AuthLevel.USER.isSatisfiedBy(ActorRole.ADMIN));
            assertTrue(AuthLevel.ADMIN.isSatisfiedBy(ActorRole.ADMIN));
            assertFalse(AuthLevel.ROOT.isSatisfiedBy(ActorRole.ADMIN));
            assertTrue(AuthLevel.ROOT.isSatisfiedBy(ActorRole.ROOT));
            assertFalse(AuthLevel.ADMIN.isSatisfiedBy(ActorRole.COMMON));
        }
    }

    @Nested
    @DisplayName("AuthenticatedActor 构造不变量")
    class Construction {

        @Test
        @DisplayName("合法主体构造成功")
        void valid() {
            AuthenticatedActor a = new AuthenticatedActor(42L, "alice", ActorRole.ADMIN);
            assertEquals(42L, a.userId());
            assertEquals("alice", a.username());
            assertEquals(ActorRole.ADMIN, a.role());
        }

        @Test
        @DisplayName("非正 userId 构造期拒绝")
        void nonPositiveUserId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AuthenticatedActor(0L, "x", ActorRole.COMMON));
            assertThrows(IllegalArgumentException.class,
                    () -> new AuthenticatedActor(-1L, "x", ActorRole.COMMON));
        }

        @Test
        @DisplayName("role 为空构造期拒绝")
        void nullRole() {
            assertThrows(NullPointerException.class,
                    () -> new AuthenticatedActor(1L, "x", null));
        }
    }

    @Nested
    @DisplayName("requireAtLeast 级别护栏（F-5031）")
    class RequireAtLeast {

        @Test
        @DisplayName("满足级别放行")
        void allowed() {
            AuthenticatedActor admin = new AuthenticatedActor(1L, "a", ActorRole.ADMIN);
            assertDoesNotThrow(() -> admin.requireAtLeast(AuthLevel.USER));
            assertDoesNotThrow(() -> admin.requireAtLeast(AuthLevel.ADMIN));
            AuthenticatedActor root = new AuthenticatedActor(2L, "r", ActorRole.ROOT);
            assertDoesNotThrow(() -> root.requireAtLeast(AuthLevel.ROOT));
        }

        @Test
        @DisplayName("级别不足越权拒绝（403 语义）")
        void denied() {
            AuthenticatedActor common = new AuthenticatedActor(1L, "c", ActorRole.COMMON);
            assertThrows(AccessDeniedException.class, () -> common.requireAtLeast(AuthLevel.ADMIN));
            AuthenticatedActor admin = new AuthenticatedActor(2L, "a", ActorRole.ADMIN);
            assertThrows(AccessDeniedException.class, () -> admin.requireAtLeast(AuthLevel.ROOT));
        }
    }

    @Nested
    @DisplayName("requireSelfScopeOrAdmin 自身资源护栏（F-5032）")
    class SelfScope {

        @Test
        @DisplayName("访问本人资源放行")
        void ownResource() {
            AuthenticatedActor user = new AuthenticatedActor(7L, "u", ActorRole.COMMON);
            assertDoesNotThrow(() -> user.requireSelfScopeOrAdmin(7L));
        }

        @Test
        @DisplayName("admin+ 访问他人资源放行（全量权限）")
        void adminOverride() {
            AuthenticatedActor admin = new AuthenticatedActor(7L, "a", ActorRole.ADMIN);
            assertDoesNotThrow(() -> admin.requireSelfScopeOrAdmin(999L));
            AuthenticatedActor root = new AuthenticatedActor(8L, "r", ActorRole.ROOT);
            assertDoesNotThrow(() -> root.requireSelfScopeOrAdmin(999L));
        }

        @Test
        @DisplayName("common 访问他人资源越权拒绝")
        void crossUserDenied() {
            AuthenticatedActor user = new AuthenticatedActor(7L, "u", ActorRole.COMMON);
            assertThrows(AccessDeniedException.class, () -> user.requireSelfScopeOrAdmin(999L));
        }
    }

    @Nested
    @DisplayName("requireHigherThan 角色层级管理护栏（AC-10）")
    class RequireHigherThan {

        @Test
        @DisplayName("操作者严格高于目标放行")
        void higherAllowed() {
            AuthenticatedActor admin = new AuthenticatedActor(1L, "a", ActorRole.ADMIN);
            assertDoesNotThrow(() -> admin.requireHigherThan(ActorRole.COMMON));
            AuthenticatedActor root = new AuthenticatedActor(2L, "r", ActorRole.ROOT);
            assertDoesNotThrow(() -> root.requireHigherThan(ActorRole.ADMIN));
        }

        @Test
        @DisplayName("操作者不高于目标（同级/更低）越权拒绝")
        void notHigherDenied() {
            AuthenticatedActor admin = new AuthenticatedActor(1L, "a", ActorRole.ADMIN);
            assertThrows(AccessDeniedException.class, () -> admin.requireHigherThan(ActorRole.ADMIN));
            assertThrows(AccessDeniedException.class, () -> admin.requireHigherThan(ActorRole.ROOT));
        }
    }
}
