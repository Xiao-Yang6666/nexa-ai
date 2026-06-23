package com.nexa.account.provider.domain.model;

import com.nexa.account.provider.domain.exception.InvalidAccountParameterException;
import com.nexa.account.provider.domain.vo.AccountGroupRef;
import com.nexa.account.provider.domain.vo.AccountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Account} 供应商账号聚合根单测（纯 JUnit，零 Spring/DB）。
 *
 * <p>覆盖核心领域规则，按正常/边界/异常组织：创建不变量（name/platform/type 必填、并发/优先级归一、
 * 默认启用）、覆盖式编辑 + credentials 可选保留、启停/限流状态迁移、isSchedulable 调度判定。</p>
 */
@DisplayName("Account 供应商账号聚合根")
class AccountTest {

    // ---- create：必填 + 默认值 + 归一 ----

    @Test
    @DisplayName("create：合法入参 → 默认启用、concurrency=3、priority=50、autoPause=true")
    void createNormal() {
        Account a = Account.create("acc1", "openai", "api_key", "{\"key\":\"sk-x\"}",
                null, null, null, null, null);

        assertNull(a.id(), "未持久化 id 为 null");
        assertEquals("acc1", a.name());
        assertEquals("openai", a.platform());
        assertEquals("api_key", a.type());
        assertEquals(AccountStatus.ACTIVE, a.status(), "创建默认启用");
        assertEquals(3, a.concurrency(), "concurrency 缺省 3");
        assertEquals(50, a.priority(), "priority 缺省 50");
        assertTrue(a.autoPauseOnExpired(), "autoPauseOnExpired 缺省 true");
        assertTrue(a.groups().isEmpty());
    }

    @Test
    @DisplayName("create：concurrency<1 → 归一为默认 3；priority<0 → 归一为 50")
    void createNormalizesConcurrencyAndPriority() {
        Account a = Account.create("acc", "openai", "api_key", null, 0, -5, null, null, null);
        assertEquals(3, a.concurrency());
        assertEquals(50, a.priority());
    }

    @Test
    @DisplayName("create：显式 concurrency/priority 生效")
    void createExplicitValues() {
        Account a = Account.create("acc", "anthropic", "oauth", null, 10, 80, null, false, null);
        assertEquals(10, a.concurrency());
        assertEquals(80, a.priority());
        assertFalse(a.autoPauseOnExpired());
    }

    @Test
    @DisplayName("create：name 缺失 → 400 文案")
    void createMissingName() {
        InvalidAccountParameterException e = assertThrows(InvalidAccountParameterException.class,
                () -> Account.create("  ", "openai", "api_key", null, null, null, null, null, null));
        assertEquals("name is required", e.getMessage());
    }

    @Test
    @DisplayName("create：platform 缺失 → 400 文案")
    void createMissingPlatform() {
        InvalidAccountParameterException e = assertThrows(InvalidAccountParameterException.class,
                () -> Account.create("acc", null, "api_key", null, null, null, null, null, null));
        assertEquals("platform is required", e.getMessage());
    }

    @Test
    @DisplayName("create：type 缺失 → 400 文案")
    void createMissingType() {
        InvalidAccountParameterException e = assertThrows(InvalidAccountParameterException.class,
                () -> Account.create("acc", "openai", "", null, null, null, null, null, null));
        assertEquals("type is required", e.getMessage());
    }

    @Test
    @DisplayName("create：携带分组关联")
    void createWithGroups() {
        Account a = Account.create("acc", "openai", "api_key", null, null, null, null, null,
                List.of(new AccountGroupRef(1L, 50), new AccountGroupRef(2L, 90)));
        assertEquals(2, a.groups().size());
    }

    // ---- update：覆盖式 + credentials 可选保留 ----

    @Test
    @DisplayName("update：覆盖式更新字段；credentials 空白 → 保留原值")
    void updateKeepsCredentialsWhenBlank() {
        Account a = Account.create("acc", "openai", "api_key", "{\"key\":\"original\"}",
                null, null, null, null, null);
        a.update("acc2", "anthropic", "oauth", "  ", 5, 70, 9999L, false, null);

        assertEquals("acc2", a.name());
        assertEquals("anthropic", a.platform());
        assertEquals("oauth", a.type());
        assertEquals("{\"key\":\"original\"}", a.credentials(), "空白 credentials 保留原值");
        assertEquals(5, a.concurrency());
        assertEquals(70, a.priority());
        assertFalse(a.autoPauseOnExpired());
    }

    @Test
    @DisplayName("update：显式新 credentials → 替换")
    void updateReplacesCredentials() {
        Account a = Account.create("acc", "openai", "api_key", "{\"key\":\"original\"}",
                null, null, null, null, null);
        a.update("acc", "openai", "api_key", "{\"key\":\"new\"}", null, null, null, null, null);
        assertEquals("{\"key\":\"new\"}", a.credentials());
    }

    @Test
    @DisplayName("update：name 缺失 → 400")
    void updateMissingName() {
        Account a = Account.create("acc", "openai", "api_key", null, null, null, null, null, null);
        assertThrows(InvalidAccountParameterException.class,
                () -> a.update(null, "openai", "api_key", null, null, null, null, null, null));
    }

    // ---- 状态迁移：enable/disable/markRateLimited/recoverFromRateLimit ----

    @Test
    @DisplayName("disable → DISABLED；enable → ACTIVE 并清限流痕迹")
    void enableDisableTransition() {
        Account a = Account.create("acc", "openai", "api_key", null, null, null, null, null, null);
        a.disable();
        assertEquals(AccountStatus.DISABLED, a.status());
        a.enable();
        assertEquals(AccountStatus.ACTIVE, a.status());
    }

    @Test
    @DisplayName("markRateLimited → RATE_LIMITED + 记录 resetAt")
    void markRateLimited() {
        Account a = Account.create("acc", "openai", "api_key", null, null, null, null, null, null);
        a.markRateLimited(123456L);
        assertEquals(AccountStatus.RATE_LIMITED, a.status());
        assertEquals(123456L, a.rateLimitResetAt());
        assertTrue(a.rateLimitedAt() != null);
    }

    @Test
    @DisplayName("recoverFromRateLimit：限流态 → 恢复 ACTIVE 返回 true；非限流态 → false 无副作用")
    void recoverFromRateLimit() {
        Account a = Account.create("acc", "openai", "api_key", null, null, null, null, null, null);
        assertFalse(a.recoverFromRateLimit(), "ACTIVE 态无需恢复");

        a.markRateLimited(1L);
        assertTrue(a.recoverFromRateLimit(), "限流态恢复");
        assertEquals(AccountStatus.ACTIVE, a.status());
        assertNull(a.rateLimitResetAt(), "恢复后清痕迹");
    }

    // ---- isSchedulable：调度判定 ----

    @Test
    @DisplayName("isSchedulable：ACTIVE 未过期未过载 → 可调度")
    void schedulableWhenActive() {
        Account a = Account.create("acc", "openai", "api_key", null, null, null, null, null, null);
        assertTrue(a.isSchedulable(Instant.now().getEpochSecond()));
    }

    @Test
    @DisplayName("isSchedulable：DISABLED / RATE_LIMITED → 不可调度")
    void notSchedulableWhenNotActive() {
        Account a = Account.create("acc", "openai", "api_key", null, null, null, null, null, null);
        long now = Instant.now().getEpochSecond();
        a.disable();
        assertFalse(a.isSchedulable(now));
        a.markRateLimited(null);
        assertFalse(a.isSchedulable(now));
    }

    @Test
    @DisplayName("isSchedulable：autoPause 且已过期 → 不可调度")
    void notSchedulableWhenExpired() {
        long now = Instant.now().getEpochSecond();
        Account a = Account.create("acc", "openai", "api_key", null, null, null, now - 100, true, null);
        assertFalse(a.isSchedulable(now));
    }

    @Test
    @DisplayName("isSchedulable：autoPause=false 即使过期仍可调度")
    void schedulableWhenExpiredButAutoPauseOff() {
        long now = Instant.now().getEpochSecond();
        Account a = Account.create("acc", "openai", "api_key", null, null, null, now - 100, false, null);
        assertTrue(a.isSchedulable(now));
    }
}
