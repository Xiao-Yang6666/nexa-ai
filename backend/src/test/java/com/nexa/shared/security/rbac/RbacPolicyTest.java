package com.nexa.shared.security.rbac;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RBAC 角色×操作域授权矩阵单测（纯 JUnit，不起 Spring/DB）。
 *
 * <p>覆盖 ROLE-PERMISSION-MATRIX §3 矩阵（F-5034）的核心判定：各系统角色的允许操作域集合、
 * 权限单调递增（高角色 ⊇ 低角色）、root 专属 O12、common 不可触管理域。按授权/拒绝/集合快照不可变三类
 * 组织（backend-engineer §3.3）。</p>
 */
@DisplayName("RbacPolicy 角色×操作域授权矩阵")
class RbacPolicyTest {

    private final RbacPolicy policy = new RbacPolicy();

    @Nested
    @DisplayName("common 角色")
    class Common {

        @Test
        @DisplayName("允许公开浏览 + 自身资源域 + 自助日志")
        void allowed() {
            assertTrue(policy.isAllowed(ActorRole.COMMON, OperationDomain.O01_PUBLIC_BROWSE));
            assertTrue(policy.isAllowed(ActorRole.COMMON, OperationDomain.O02_ACCOUNT_IDENTITY));
            assertTrue(policy.isAllowed(ActorRole.COMMON, OperationDomain.O03_TOKEN_SELF));
            assertTrue(policy.isAllowed(ActorRole.COMMON, OperationDomain.O10_LOG_AUDIT));
        }

        @Test
        @DisplayName("拒绝渠道/计费/用户管理/运维/系统设置")
        void denied() {
            assertFalse(policy.isAllowed(ActorRole.COMMON, OperationDomain.O07_CHANNEL_MANAGE));
            assertFalse(policy.isAllowed(ActorRole.COMMON, OperationDomain.O08_BILLING_CONFIG));
            assertFalse(policy.isAllowed(ActorRole.COMMON, OperationDomain.O09_USER_MANAGE));
            assertFalse(policy.isAllowed(ActorRole.COMMON, OperationDomain.O11_OPS));
            assertFalse(policy.isAllowed(ActorRole.COMMON, OperationDomain.O12_SYSTEM_SETTINGS));
        }
    }

    @Nested
    @DisplayName("admin 角色")
    class Admin {

        @Test
        @DisplayName("允许渠道/计费/用户管理/运维 + 继承 common 全集")
        void allowed() {
            assertTrue(policy.isAllowed(ActorRole.ADMIN, OperationDomain.O07_CHANNEL_MANAGE));
            assertTrue(policy.isAllowed(ActorRole.ADMIN, OperationDomain.O08_BILLING_CONFIG));
            assertTrue(policy.isAllowed(ActorRole.ADMIN, OperationDomain.O09_USER_MANAGE));
            assertTrue(policy.isAllowed(ActorRole.ADMIN, OperationDomain.O11_OPS));
            // 继承 common
            assertTrue(policy.isAllowed(ActorRole.ADMIN, OperationDomain.O02_ACCOUNT_IDENTITY));
        }

        @Test
        @DisplayName("拒绝系统设置（O12 root 专属）")
        void deniedSystemSettings() {
            assertFalse(policy.isAllowed(ActorRole.ADMIN, OperationDomain.O12_SYSTEM_SETTINGS));
        }
    }

    @Nested
    @DisplayName("root 角色")
    class Root {

        @Test
        @DisplayName("允许全部操作域含系统设置")
        void allowsEverything() {
            for (OperationDomain op : OperationDomain.values()) {
                assertTrue(policy.isAllowed(ActorRole.ROOT, op),
                        "root 应被授权 " + op.code());
            }
        }
    }

    @Nested
    @DisplayName("权限单调递增 + 快照不可变")
    class Invariants {

        @Test
        @DisplayName("高角色允许集 ⊇ 低角色允许集")
        void monotonic() {
            Set<OperationDomain> common = policy.allowedOperations(ActorRole.COMMON);
            Set<OperationDomain> admin = policy.allowedOperations(ActorRole.ADMIN);
            Set<OperationDomain> root = policy.allowedOperations(ActorRole.ROOT);
            assertTrue(admin.containsAll(common));
            assertTrue(root.containsAll(admin));
        }

        @Test
        @DisplayName("allowedOperations 返回防御性拷贝，外部篡改不影响矩阵")
        void defensiveCopy() {
            Set<OperationDomain> admin = policy.allowedOperations(ActorRole.ADMIN);
            admin.clear();
            // 再次取仍完整（说明返回的是拷贝，内部矩阵未被污染）。
            assertTrue(policy.isAllowed(ActorRole.ADMIN, OperationDomain.O09_USER_MANAGE));
        }

        @Test
        @DisplayName("actor 重载与 role 重载结果一致")
        void actorOverloadConsistent() {
            AuthenticatedActor admin = new AuthenticatedActor(1L, "a", ActorRole.ADMIN);
            assertEquals(
                    policy.isAllowed(ActorRole.ADMIN, OperationDomain.O09_USER_MANAGE),
                    policy.isAllowed(admin, OperationDomain.O09_USER_MANAGE));
        }
    }
}
