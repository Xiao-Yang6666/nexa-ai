package com.nexa.account.domain.model;

import com.nexa.account.domain.vo.Email;
import com.nexa.account.domain.vo.Role;
import com.nexa.account.domain.vo.UserStatus;
import com.nexa.account.domain.vo.Username;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link User#anonymize()} 单测（纯 JUnit）——F-5020 账号注销 PII 匿名化，DC-003/DC-011。
 *
 * <p>验证注销后 PII 被清空/匿名（验收「注销后 PII 清空或匿名」），计量字段保留，
 * 密码不可登录、用户名替换为不可逆唯一占位、状态禁用。</p>
 */
@DisplayName("用户注销匿名化")
class UserAnonymizeTest {

    private User persistedUser(long id) {
        // 用 rehydrate 造一个已持久化（含 id）的用户，带完整 PII + 计量字段。
        return User.rehydrate(
                id,
                Username.of("alice"),
                "$2a$10$realhashplaceholder",
                Email.of("alice@example.com"),
                Role.COMMON,
                UserStatus.ENABLED,
                1000L,
                "AB12",
                0L,
                123456L,
                "Alice Display",
                "{\"lang\":\"zh\"}",
                "vip",
                "admin remark with PII",
                500L,   // usedQuota（计量级，应保留）
                42L,    // requestCount（计量级，应保留）
                111L);  // createdAt（保留）
    }

    @Test
    @DisplayName("匿名化：PII 清空、用户名替换为 deleted_<id>、状态禁用")
    void anonymizeClearsPii() {
        User u = persistedUser(77L);
        long usedQuotaBefore = u.usedQuota();
        long reqCountBefore = u.requestCount();

        u.anonymize();

        // 用户名 → 不可逆唯一占位
        assertEquals("deleted_77", u.username().value());
        // PII 清空
        assertNull(u.email(), "邮箱清空");
        assertNull(u.displayName(), "展示名清空");
        assertNull(u.remark(), "备注清空");
        assertNull(u.setting(), "设置清空");
        // 密码置不可登录占位（不再是原哈希）
        assertNotEquals("$2a$10$realhashplaceholder", u.passwordHash());
        // 状态禁用
        assertEquals(UserStatus.DISABLED, u.status());
        // 计量字段保留（DC-001 计量级聚合保留）
        assertEquals(usedQuotaBefore, u.usedQuota(), "已用额度保留");
        assertEquals(reqCountBefore, u.requestCount(), "请求计数保留");
    }

    @Test
    @DisplayName("匿名化幂等：重复调用生成相同占位")
    void anonymizeIdempotent() {
        User u = persistedUser(5L);
        u.anonymize();
        String first = u.username().value();
        u.anonymize();
        assertEquals(first, u.username().value());
    }

    @Test
    @DisplayName("未持久化用户（无 id）匿名化 → 抛 IllegalStateException")
    void anonymizeRequiresId() {
        User u = User.rehydrate(
                null, Username.of("bob"), "hash", null,
                Role.COMMON, UserStatus.ENABLED, 0L, "CD34", 0L, 0L);
        assertThrows(IllegalStateException.class, u::anonymize);
    }

    @Test
    @DisplayName("匿名占位用户名长度受控（<=20，满足 username 列约束）")
    void anonymizedUsernameWithinLength() {
        User u = persistedUser(999_999_999L);
        u.anonymize();
        assertTrue(u.username().value().length() <= Username.MAX_LENGTH);
    }
}
