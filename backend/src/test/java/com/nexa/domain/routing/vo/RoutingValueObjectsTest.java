package com.nexa.domain.routing.vo;

import com.nexa.domain.routing.exception.InvalidAffinityParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由域值对象单测（纯 JUnit）：缓存键指纹、缓存条目续期/过期、策略不变量。
 *
 * <p>覆盖 F-2029/F-2031/F-2033 的值对象边界（backend-engineer §3.3 正常/边界/异常）。</p>
 */
@DisplayName("Routing 值对象")
class RoutingValueObjectsTest {

    @Test
    @DisplayName("AffinityCacheKey 指纹稳定且不可还原（同明文同指纹、异明文异指纹、32 hex 字符）")
    void cacheKeyFingerprint() {
        AffinityCacheKey k1 = new AffinityCacheKey("codex", "user_42", "default");
        AffinityCacheKey k2 = new AffinityCacheKey("codex", "user_42", "default");
        AffinityCacheKey k3 = new AffinityCacheKey("codex", "user_99", "default");
        assertEquals(k1.fingerprint(), k2.fingerprint());
        assertNotEquals(k1.fingerprint(), k3.fingerprint());
        assertEquals(32, k1.fingerprint().length());
        assertFalse(k1.fingerprint().contains("user_42")); // 不含明文
    }

    @Test
    @DisplayName("AffinityCacheKey 空 rule_name/raw_key → 抛异常")
    void cacheKeyValidation() {
        assertThrows(InvalidAffinityParameterException.class, () -> new AffinityCacheKey("", "k", null));
        assertThrows(InvalidAffinityParameterException.class, () -> new AffinityCacheKey("r", " ", null));
    }

    @Test
    @DisplayName("AffinityCacheEntry firstHit/renew/isExpired 行为")
    void cacheEntryLifecycle() {
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        AffinityCacheEntry e = AffinityCacheEntry.firstHit(10L, t0, 60L);
        assertEquals(1L, e.hitCount());
        assertFalse(e.isExpired(t0.plusSeconds(59)));
        assertTrue(e.isExpired(t0.plusSeconds(60))); // now>=expiresAt 即过期

        AffinityCacheEntry renewed = e.renew(t0.plusSeconds(30), 60L);
        assertEquals(2L, renewed.hitCount());
        assertEquals(10L, renewed.channelId());
        assertFalse(renewed.isExpired(t0.plusSeconds(89)));
    }

    @Test
    @DisplayName("AffinityCacheEntry 非法 channelId/ttl → 抛异常")
    void cacheEntryValidation() {
        Instant t0 = Instant.now();
        assertThrows(InvalidAffinityParameterException.class, () -> AffinityCacheEntry.firstHit(0L, t0, 60L));
        assertThrows(InvalidAffinityParameterException.class, () -> AffinityCacheEntry.firstHit(1L, t0, 0L));
    }

    @Test
    @DisplayName("AffinitySettings 不变量：maxEntries/defaultTtl 至少 1")
    void settingsValidation() {
        assertThrows(InvalidAffinityParameterException.class, () -> new AffinitySettings(true, true, 0, 3600L));
        assertThrows(InvalidAffinityParameterException.class, () -> new AffinitySettings(true, true, 100, 0L));
        AffinitySettings d = AffinitySettings.defaults();
        assertTrue(d.enabled());
        assertTrue(d.switchOnSuccess());
    }

    @Test
    @DisplayName("KeySource 空 path → 抛异常；KeySourceType 未知值回退 GJSON")
    void keySourceValidation() {
        assertThrows(InvalidAffinityParameterException.class, () -> new KeySource(KeySourceType.GJSON, " "));
        assertEquals(KeySourceType.GJSON, KeySourceType.fromWire("nonsense"));
        assertEquals(KeySourceType.REQUEST_HEADER, KeySourceType.fromWire("request_header"));
    }
}
