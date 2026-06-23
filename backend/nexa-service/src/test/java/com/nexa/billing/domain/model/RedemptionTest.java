package com.nexa.billing.domain.model;

import com.nexa.billing.domain.exception.RedemptionAlreadyUsedException;
import com.nexa.billing.domain.exception.RedemptionExpiredException;
import com.nexa.billing.domain.vo.Quota;
import com.nexa.billing.domain.vo.RedemptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Redemption} 兑换码聚合根单测（纯 JUnit，不起 Spring/DB）。
 *
 * <p>覆盖 prd-billing BL-4 验收标准：一次性 / 过期 / 已用守卫（按正常/边界/异常三类组织，
 * backend-engineer §3.3）。</p>
 */
@DisplayName("Redemption 兑换码聚合根")
class RedemptionTest {

    private static final long NOW = 1_700_000_000L;

    @Nested
    @DisplayName("create 生成工厂")
    class Create {

        @Test
        @DisplayName("新码为未使用态、随机 32 位 Key、面额正确")
        void newCodeIsUnused() {
            Redemption r = Redemption.create(1, "batch-A", Quota.of(500L), 0L, NOW);
            assertEquals(RedemptionStatus.UNUSED, r.status());
            assertNotNull(r.key());
            assertEquals(Redemption.KEY_LENGTH, r.key().length());
            assertEquals(500L, r.quota().value());
            assertEquals(0L, r.expiredTime());
        }
    }

    @Nested
    @DisplayName("redeem 兑换守卫")
    class Redeem {

        @Test
        @DisplayName("有效未使用码 → 入账面额、置已用、写核销人/时间")
        void validRedeem() {
            Redemption r = Redemption.create(1, "n", Quota.of(300L), 0L, NOW);
            Quota credited = r.redeem(42L, NOW + 10);
            assertEquals(300L, credited.value());
            assertEquals(RedemptionStatus.USED, r.status());
            assertEquals(42, r.usedUserId());
            assertEquals(NOW + 10, r.redeemedTime());
        }

        @Test
        @DisplayName("重复兑换已使用码 → 抛 AlreadyUsed，不重复入账")
        void doubleRedeemRejected() {
            Redemption r = Redemption.create(1, "n", Quota.of(300L), 0L, NOW);
            r.redeem(42L, NOW);
            assertThrows(RedemptionAlreadyUsedException.class, () -> r.redeem(43L, NOW));
        }

        @Test
        @DisplayName("已过期码（expiredTime 非 0 且已到）→ 抛 Expired")
        void expiredRejected() {
            Redemption r = Redemption.create(1, "n", Quota.of(300L), NOW + 100, NOW);
            assertThrows(RedemptionExpiredException.class, () -> r.redeem(42L, NOW + 200));
        }

        @Test
        @DisplayName("expiredTime=0（永不过期）→ 任意时间可兑换")
        void neverExpires() {
            Redemption r = Redemption.create(1, "n", Quota.of(300L), 0L, NOW);
            Quota credited = r.redeem(42L, NOW + 999_999_999L);
            assertEquals(300L, credited.value());
        }

        @Test
        @DisplayName("过期时间恰好等于 now → 视为已过期（>= 边界）")
        void expiryBoundary() {
            Redemption r = Redemption.create(1, "n", Quota.of(300L), NOW + 100, NOW);
            assertThrows(RedemptionExpiredException.class, () -> r.redeem(42L, NOW + 100));
        }

        @Test
        @DisplayName("已禁用码 → 不可兑换")
        void disabledRejected() {
            Redemption r = Redemption.rehydrate(1L, 1, "K".repeat(32), RedemptionStatus.DISABLED,
                    "n", Quota.of(300L), NOW, null, null, 0L);
            assertTrue(assertThrows(RedemptionAlreadyUsedException.class,
                    () -> r.redeem(42L, NOW)) != null);
        }
    }
}
