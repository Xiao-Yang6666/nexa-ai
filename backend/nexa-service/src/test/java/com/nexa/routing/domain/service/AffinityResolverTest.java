package com.nexa.routing.domain.service;

import com.nexa.routing.domain.model.AffinityRule;
import com.nexa.routing.domain.repository.AffinityCacheRepository;
import com.nexa.routing.domain.vo.AffinityCacheEntry;
import com.nexa.routing.domain.vo.AffinityCacheKey;
import com.nexa.routing.domain.vo.AffinityDecision;
import com.nexa.routing.domain.vo.AffinityRequestContext;
import com.nexa.routing.domain.vo.AffinitySettings;
import com.nexa.routing.domain.vo.KeySource;
import com.nexa.routing.domain.vo.KeySourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AffinityResolver} 领域服务单测（纯 JUnit，零 Spring/DB）。
 *
 * <p>覆盖 PRD CH-4 全部判定分支（F-2029/F-2030/F-2034）：无规则无亲和 / 命中无缓存 /
 * 命中有效缓存粘连 / 键提取失败 / 成功回写 / SwitchOnSuccess=false 不回写 / 总开关关闭。</p>
 */
@DisplayName("AffinityResolver 会话亲和解析服务")
class AffinityResolverTest {

    /** 内存 mock 缓存仓储。 */
    private static class MemoryCacheRepo implements AffinityCacheRepository {
        final Map<String, AffinityCacheEntry> store = new ConcurrentHashMap<>();

        private String k(AffinityCacheKey key) {
            return key.ruleName() + "|" + key.fingerprint() + "|" + key.usingGroup();
        }

        @Override
        public Optional<AffinityCacheEntry> find(AffinityCacheKey key) {
            return Optional.ofNullable(store.get(k(key)));
        }

        @Override
        public void put(AffinityCacheKey key, AffinityCacheEntry entry) {
            store.put(k(key), entry);
        }

        @Override
        public long clearAll() {
            long n = store.size();
            store.clear();
            return n;
        }

        @Override
        public long clearByRule(String ruleName) {
            long n = store.entrySet().stream().filter(e -> e.getKey().startsWith(ruleName + "|")).count();
            store.entrySet().removeIf(e -> e.getKey().startsWith(ruleName + "|"));
            return n;
        }

        @Override
        public Optional<Map<String, Object>> queryUsageStats(AffinityCacheKey key) {
            return Optional.empty();
        }
    }

    private final MemoryCacheRepo cacheRepo = new MemoryCacheRepo();
    private final AffinityResolver resolver = new AffinityResolver(cacheRepo);
    private final AffinitySettings settings = AffinitySettings.defaults();
    private final Instant t0 = Instant.parse("2024-01-01T00:00:00Z");

    private AffinityRule gptRule;

    @BeforeEach
    void setUp() {
        gptRule = AffinityRule.custom("test", true, "^gpt-.*", "^/v1/responses$",
                List.of(new KeySource(KeySourceType.GJSON, "prompt_cache_key")),
                Map.of("OpenAI-Beta", "responses=experimental"), true, 60L);
    }

    private static AffinityRequestContext ctx(String cacheKey) {
        return new AffinityRequestContext() {
            @Override
            public String readJsonPath(String path) {
                return "prompt_cache_key".equals(path) ? cacheKey : null;
            }

            @Override
            public String readHeader(String h) {
                return null;
            }

            @Override
            public Optional<Integer> readContextInt(String k) {
                return Optional.empty();
            }

            @Override
            public Optional<String> readContextString(String k) {
                return Optional.empty();
            }
        };
    }

    @Test
    @DisplayName("无启用规则 → noAffinity（无亲和直走普通选渠）")
    void noRulesNoAffinity() {
        AffinityDecision d = resolver.resolve("gpt-4o", "/v1/responses", null, ctx("key1"), List.of(), settings, t0);
        assertFalse(d.hasMatch());
        assertFalse(d.hasStickyChannel());
    }

    @Test
    @DisplayName("总开关关闭 → noAffinity")
    void settingsDisabledNoAffinity() {
        AffinitySettings off = new AffinitySettings(false, true, 100000, 3600L);
        AffinityDecision d = resolver.resolve("gpt-4o", "/v1/responses", null, ctx("k"), List.of(gptRule), off, t0);
        assertFalse(d.hasMatch());
    }

    @Test
    @DisplayName("命中规则但缓存未命中 → matchedNoStick（有 match，无粘连，有 header 透传）")
    void matchedButNoCacheHit() {
        AffinityDecision d = resolver.resolve("gpt-4o", "/v1/responses", null, ctx("key1"),
                List.of(gptRule), settings, t0);
        assertTrue(d.hasMatch());
        assertFalse(d.hasStickyChannel());
        assertEquals("responses=experimental", d.passHeaders().get("OpenAI-Beta"));
        assertTrue(d.skipRetryOnFailure());
    }

    @Test
    @DisplayName("命中规则且缓存命中未过期 → sticky（复用上次成功渠道）")
    void matchedCacheHitSticky() {
        // 先手动写入一条未过期缓存（channel_id=777）。
        AffinityCacheKey key = new AffinityCacheKey("test", "key1", null);
        cacheRepo.put(key, AffinityCacheEntry.firstHit(777L, t0, 3600L));

        AffinityDecision d = resolver.resolve("gpt-4o", "/v1/responses", null, ctx("key1"),
                List.of(gptRule), settings, t0.plusSeconds(1));
        assertTrue(d.hasStickyChannel());
        assertEquals(777L, d.stickyChannel().get());
    }

    @Test
    @DisplayName("缓存过期 → 不粘连（回退选渠），过期 entry 仍被命中键继续判")
    void expiredCacheNotticky() {
        AffinityCacheKey key = new AffinityCacheKey("test", "key1", null);
        // TTL=1s，t0+2s 时已过期。
        cacheRepo.put(key, AffinityCacheEntry.firstHit(888L, t0, 1L));

        AffinityDecision d = resolver.resolve("gpt-4o", "/v1/responses", null, ctx("key1"),
                List.of(gptRule), settings, t0.plusSeconds(2));
        assertTrue(d.hasMatch());
        assertFalse(d.hasStickyChannel());
    }

    @Test
    @DisplayName("F-2029 键提取失败（JSON 路径缺值）→ matchedNoStick，cacheKey=null")
    void keyExtractionFails() {
        AffinityDecision d = resolver.resolve("gpt-4o", "/v1/responses", null,
                ctx(null /* null cacheKey */), List.of(gptRule), settings, t0);
        assertTrue(d.hasMatch());
        assertFalse(d.hasStickyChannel());
        assertFalse(d.cacheKeyOpt().isPresent());
    }

    @Test
    @DisplayName("F-2031 onSuccess + SwitchOnSuccess=true → 回写缓存（新条目 hitCount=1）")
    void onSuccessWritesCacheWhenEnabled() {
        AffinityDecision d = resolver.resolve("gpt-4o", "/v1/responses", null, ctx("key2"),
                List.of(gptRule), settings, t0);
        // 回写 channel_id=999。
        resolver.onSuccess(d, 999L, settings, t0.plusSeconds(1));

        AffinityCacheKey ck = new AffinityCacheKey("test", "key2", null);
        Optional<AffinityCacheEntry> entry = cacheRepo.find(ck);
        assertTrue(entry.isPresent());
        assertEquals(999L, entry.get().channelId());
        assertEquals(1L, entry.get().hitCount());
    }

    @Test
    @DisplayName("F-2031 SwitchOnSuccess=false → onSuccess 不写缓存")
    void onSuccessNoWriteWhenSwitchOff() {
        AffinitySettings noSwitch = new AffinitySettings(true, false, 100000, 3600L);
        AffinityDecision d = resolver.resolve("gpt-4o", "/v1/responses", null, ctx("key3"),
                List.of(gptRule), noSwitch, t0);
        resolver.onSuccess(d, 555L, noSwitch, t0.plusSeconds(1));

        AffinityCacheKey ck = new AffinityCacheKey("test", "key3", null);
        assertFalse(cacheRepo.find(ck).isPresent());
    }

    @Test
    @DisplayName("F-2031 续期：同渠道 onSuccess 多次 → hitCount 递增")
    void renewalIncrementsHitCount() {
        AffinityCacheKey ck = new AffinityCacheKey("test", "key4", null);
        cacheRepo.put(ck, AffinityCacheEntry.firstHit(101L, t0, 3600L));

        AffinityDecision d = resolver.resolve("gpt-4o", "/v1/responses", null, ctx("key4"),
                List.of(gptRule), settings, t0.plusSeconds(1));
        assertTrue(d.hasStickyChannel()); // 复用
        resolver.onSuccess(d, 101L, settings, t0.plusSeconds(2));

        Optional<AffinityCacheEntry> entry = cacheRepo.find(ck);
        assertTrue(entry.isPresent());
        assertEquals(101L, entry.get().channelId());
        assertTrue(entry.get().hitCount() >= 2); // 命中续期 + 回写续期
    }
}
