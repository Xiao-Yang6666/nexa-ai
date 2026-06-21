package com.nexa.token.domain.model;

import com.nexa.token.domain.exception.InvalidTokenParameterException;
import com.nexa.token.domain.vo.TokenStatus;
import com.nexa.token.domain.vo.UsageSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Token} 聚合根单测（纯 JUnit，零 Spring/DB）。
 *
 * <p>覆盖令牌域核心领域规则（F-3001~F-3012），按正常/边界/异常组织（backend-engineer §3.3）：
 * 创建不变量（userId>0、name 必填≤50、status 默认启用、key 自动生成）、覆盖式编辑 + status_only、
 * self-scope 归属校验、用量摘要派生、key 脱敏。</p>
 */
@DisplayName("Token 聚合根")
class TokenTest {

    // ---- create：必填 + 默认值 + 校验 ----

    @Test
    @DisplayName("create：userId/name 合法 → 默认启用、key 自动生成、usedQuota=0、group 缺省空串")
    void createNormal() {
        Token t = Token.create(1L, "my-token", null, null, null, null, null, null, null, null);

        assertNull(t.id(), "未持久化 id 为 null");
        assertEquals(1L, t.userId());
        assertNotNull(t.key(), "key 自动生成");
        assertTrue(t.key().startsWith("sk-"), "key 前缀 sk-");
        assertEquals(TokenStatus.ENABLED, t.status(), "创建默认启用");
        assertEquals(0L, t.remainQuota());
        assertEquals(0L, t.usedQuota());
        assertFalse(t.unlimitedQuota());
        assertEquals("", t.group(), "group 缺省空串");
        assertEquals(Token.NEVER_EXPIRE, t.expiredTime(), "expiredTime 缺省 -1");
        assertNotNull(t.createdTime());
    }

    @Test
    @DisplayName("create：userId 非正 → 抛 InvalidTokenParameterException")
    void createInvalidUserId() {
        InvalidTokenParameterException ex = assertThrows(InvalidTokenParameterException.class,
                () -> Token.create(0, "n", null, null, null, null, null, null, null, null));
        assertTrue(ex.getMessage().contains("userId must be positive"));
    }

    @Test
    @DisplayName("create：name 空白 → 抛 InvalidTokenParameterException")
    void createBlankName() {
        InvalidTokenParameterException ex = assertThrows(InvalidTokenParameterException.class,
                () -> Token.create(1, "  ", null, null, null, null, null, null, null, null));
        assertTrue(ex.getMessage().contains("name must not be blank"));
    }

    @Test
    @DisplayName("create：name 超长（>50）→ 抛 InvalidTokenParameterException")
    void createNameTooLong() {
        String longName = "x".repeat(51);
        InvalidTokenParameterException ex = assertThrows(InvalidTokenParameterException.class,
                () -> Token.create(1, longName, null, null, null, null, null, null, null, null));
        assertTrue(ex.getMessage().contains("name too long"));
    }

    @Test
    @DisplayName("create：unlimited_quota=false + quota 越界 → 抛 InvalidTokenParameterException")
    void createQuotaOutOfRange() {
        // 负数
        assertThrows(InvalidTokenParameterException.class,
                () -> Token.create(1, "n", -1L, false, null, null, null, null, null, null));
        // 超上限
        long huge = Token.MAX_QUOTA_VALUE + 1;
        assertThrows(InvalidTokenParameterException.class,
                () -> Token.create(1, "n", huge, false, null, null, null, null, null, null));
    }

    @Test
    @DisplayName("create：unlimited_quota=true 时 quota 无需校验范围，归 0")
    void createUnlimitedQuotaIgnoreValue() {
        Token t = Token.create(1, "n", 999999999999L, true, null, null, null, null, null, null);
        assertTrue(t.unlimitedQuota());
        assertEquals(0L, t.remainQuota(), "无限额度时 quota 归 0");
    }

    // ---- update (覆盖式) ----

    @Test
    @DisplayName("update：覆盖式编辑更新全部字段")
    void updateOverwrite() {
        Token t = Token.create(1, "original", 100L, false, -1L, false, "", "", "g1", false);
        t.update("renamed", 200L, true, 1800000000L, true, "[\"gpt-4\"]", "1.2.3.4", "g2", true, "[\"openai\"]");

        assertEquals("renamed", t.name());
        assertTrue(t.unlimitedQuota());
        assertEquals(0L, t.remainQuota(), "unlimited 时 quota 归 0");
        assertEquals(1800000000L, t.expiredTime());
        assertTrue(t.modelLimitsEnabled());
        assertEquals("[\"gpt-4\"]", t.modelLimits());
        assertEquals("1.2.3.4", t.allowIps());
        assertEquals("g2", t.group());
        assertTrue(t.crossGroupRetry());
        assertTrue(t.endpointLimitsEnabled(), "endpointLimits 非空即启用");
        assertEquals("[\"openai\"]", t.endpointLimits());
    }

    @Test
    @DisplayName("update：name 空白 → 抛 InvalidTokenParameterException")
    void updateBlankName() {
        Token t = Token.create(1, "n", null, null, null, null, null, null, null, null);
        assertThrows(InvalidTokenParameterException.class,
                () -> t.update("", null, null, null, null, null, null, null, null, null));
    }

    // ---- applyStatus (status_only) ----

    @Test
    @DisplayName("applyStatus：切换为禁用/启用幂等")
    void applyStatusToggle() {
        Token t = Token.create(1, "n", null, null, null, null, null, null, null, null);
        assertEquals(TokenStatus.ENABLED, t.status());

        t.applyStatus(2);
        assertEquals(TokenStatus.DISABLED, t.status());

        t.applyStatus(1);
        assertEquals(TokenStatus.ENABLED, t.status());

        // 同态幂等
        t.applyStatus(1);
        assertEquals(TokenStatus.ENABLED, t.status());
    }

    @Test
    @DisplayName("applyStatus：非法状态码 → 抛 InvalidTokenParameterException")
    void applyStatusInvalid() {
        Token t = Token.create(1, "n", null, null, null, null, null, null, null, null);
        assertThrows(InvalidTokenParameterException.class, () -> t.applyStatus(99));
        assertThrows(InvalidTokenParameterException.class, () -> t.applyStatus(null));
    }

    // ---- belongsTo (self-scope) ----

    @Test
    @DisplayName("belongsTo：归属用户判定正确")
    void belongsToCheck() {
        Token t = Token.create(1, "n", null, null, null, null, null, null, null, null);
        assertTrue(t.belongsTo(1));
        assertFalse(t.belongsTo(2));
    }

    // ---- maskedKey ----

    @Test
    @DisplayName("maskedKey：脱敏保留头 6 尾 4，中段 ***")
    void maskedKeyFormat() {
        Token t = Token.create(1, "n", null, null, null, null, null, null, null, null);
        String masked = t.maskedKey();
        // key 形如 sk-<64chars>，脱敏后头 6 + *** + 尾 4
        assertTrue(masked.startsWith("sk-"), "脱敏 key 保留前缀");
        assertTrue(masked.contains("***"), "脱敏 key 含 ***");
        assertEquals(6 + 3 + 4, masked.length(), "脱敏长度=头6+***+尾4");
    }

    // ---- usageSummary ----

    @Test
    @DisplayName("usageSummary：派生用量摘要正确")
    void usageSummaryDerive() {
        Token t = Token.rehydrate(100L, 1, "k", 1, "n", 1800000000L, 500, false,
                true, "[\"gpt-4\"]", "", 200, "", false, false, "", null, 123456L);

        UsageSummary summary = t.usageSummary();

        assertEquals("credit_summary", summary.object());
        assertEquals(700L, summary.totalGranted(), "granted = remain + used");
        assertEquals(200L, summary.totalUsed());
        assertEquals(500L, summary.totalAvailable());
        assertEquals(1800000000L, summary.expiresAt());
        assertFalse(summary.unlimitedQuota());
        assertTrue(summary.modelLimitsEnabled());
        assertEquals("[\"gpt-4\"]", summary.modelLimits());
    }

    @Test
    @DisplayName("usageSummary：永不过期 expiredTime=-1 时 expiresAt 归零")
    void usageSummaryNeverExpire() {
        Token t = Token.rehydrate(100L, 1, "k", 1, "n", Token.NEVER_EXPIRE, 0, false,
                false, "", "", 0, "", false, false, "", null, 0L);

        UsageSummary summary = t.usageSummary();
        assertEquals(0L, summary.expiresAt(), "永不过期时 expiresAt 归零");
    }

    @Test
    @DisplayName("usageSummary：无限额度时 totalAvailable=-1")
    void usageSummaryUnlimited() {
        Token t = Token.rehydrate(100L, 1, "k", 1, "n", Token.NEVER_EXPIRE, 0, true,
                false, "", "", 500, "", false, false, "", null, 0L);

        UsageSummary summary = t.usageSummary();
        assertEquals(UsageSummary.UNLIMITED_AVAILABLE, summary.totalAvailable(), "无限额度 available=-1");
        assertTrue(summary.unlimitedQuota());
    }
}
