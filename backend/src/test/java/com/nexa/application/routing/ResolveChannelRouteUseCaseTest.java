package com.nexa.application.routing;

import com.nexa.application.routing.port.ChannelSelectionPort;
import com.nexa.domain.routing.model.AffinityRule;
import com.nexa.domain.routing.repository.AffinityCacheRepository;
import com.nexa.domain.routing.repository.AffinityRuleRepository;
import com.nexa.domain.routing.service.AffinityResolver;
import com.nexa.domain.routing.service.CrossGroupRetryScheduler;
import com.nexa.domain.routing.vo.AffinityCacheEntry;
import com.nexa.domain.routing.vo.AffinityCacheKey;
import com.nexa.domain.routing.vo.AffinityRequestContext;
import com.nexa.domain.routing.vo.AffinitySettings;
import com.nexa.domain.routing.vo.ChannelCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ResolveChannelRouteUseCase} 单测（纯 JUnit，零 Spring/DB）。
 *
 * <p>覆盖选渠中间件应用层整合路径（F-2029~F-2037，PRD CH-4 + CH-5）：
 * <ul>
 *   <li>亲和粘连命中 → 直接复用粘连渠道，跳过 CH-5 抽签（F-2029）。</li>
 *   <li>无亲和 + 普通分组 → 直选满足渠道（CH-2 委托）。</li>
 *   <li>无亲和 + 普通分组无渠道 → 耗尽。</li>
 *   <li>auto 分组 → 委托 CH-5 调度（首组选中 / 全组耗尽）。</li>
 *   <li>F-2034 skipRetryOnFailure → 重试步直接耗尽。</li>
 *   <li>F-2031 onSuccess → switchOnSuccess=true 回写缓存。</li>
 * </ul>
 * 用真实领域服务 + 内存假仓储 + stub 选渠端口，验证编排逻辑（不 mock 框架）。</p>
 */
@DisplayName("ResolveChannelRouteUseCase 选渠路由编排用例")
class ResolveChannelRouteUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-06-21T00:00:00Z");

    /** 内存假亲和缓存仓储。 */
    private static class FakeCacheRepository implements AffinityCacheRepository {
        private final Map<String, AffinityCacheEntry> store = new HashMap<>();

        private static String k(AffinityCacheKey key) {
            return key.ruleName() + "|" + key.rawKey() + "|" + (key.usingGroup() == null ? "" : key.usingGroup());
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
            return 0;
        }

        @Override
        public Optional<Map<String, Object>> queryUsageStats(AffinityCacheKey key) {
            return Optional.empty();
        }
    }

    /** 内存假规则/策略仓储。 */
    private static class FakeRuleRepository implements AffinityRuleRepository {
        private final List<AffinityRule> rules;
        private AffinitySettings settings;

        FakeRuleRepository(List<AffinityRule> rules, AffinitySettings settings) {
            this.rules = rules;
            this.settings = settings;
        }

        @Override
        public void save(AffinityRule rule) { }

        @Override
        public Optional<AffinityRule> findByName(String name) {
            return rules.stream().filter(r -> r.name().equals(name)).findFirst();
        }

        @Override
        public List<AffinityRule> findEnabledRules() {
            return rules.stream().filter(AffinityRule::enabled).toList();
        }

        @Override
        public List<AffinityRule> findAll() {
            return rules;
        }

        @Override
        public void delete(String name) { }

        @Override
        public AffinitySettings loadSettings() {
            return settings;
        }

        @Override
        public void saveSettings(AffinitySettings s) {
            this.settings = s;
        }
    }

    /** stub 选渠端口：按 (group, priorityRetry) 返回预设候选（null=无渠道）。 */
    private static class StubSelection implements ChannelSelectionPort {
        private final Map<String, ChannelCandidate> table = new HashMap<>();

        StubSelection put(String group, int priorityRetry, ChannelCandidate c) {
            table.put(group + "|" + priorityRetry, c);
            return this;
        }

        @Override
        public ChannelCandidate selectChannel(String group, String model, int priorityRetry) {
            return table.get(group + "|" + priorityRetry);
        }
    }

    /** 简单请求上下文：仅支持 gjson 取值（用预置 map 模拟）。 */
    private static class MapRequestContext implements AffinityRequestContext {
        private final Map<String, String> json;

        MapRequestContext(Map<String, String> json) {
            this.json = json;
        }

        @Override
        public String readJsonPath(String jsonPath) {
            return json.get(jsonPath);
        }

        @Override
        public String readHeader(String headerName) {
            return null;
        }

        @Override
        public Optional<Integer> readContextInt(String key) {
            return Optional.empty();
        }

        @Override
        public Optional<String> readContextString(String key) {
            return Optional.empty();
        }
    }

    private ResolveChannelRouteUseCase newUseCase(FakeRuleRepository rules, FakeCacheRepository cache,
                                                  ChannelSelectionPort selection) {
        return new ResolveChannelRouteUseCase(rules, new AffinityResolver(cache),
                new CrossGroupRetryScheduler(), selection);
    }

    private static ChannelCandidate candidate(long id, String group) {
        return new ChannelCandidate(id, group, 0, 100);
    }

    @Test
    @DisplayName("亲和粘连命中：复用缓存渠道，跳过 CH-5 抽签")
    void stickyHitReusesCachedChannel() {
        FakeRuleRepository rules = new FakeRuleRepository(
                List.of(AffinityRule.builtinCodex()), AffinitySettings.defaults());
        FakeCacheRepository cache = new FakeCacheRepository();
        // 预置缓存：codex 规则 + prompt_cache_key=sess1 → channel 42（未过期）。
        AffinityCacheKey key = new AffinityCacheKey("codex", "sess1", "g1");
        cache.put(key, AffinityCacheEntry.firstHit(42L, NOW, 3600));

        StubSelection selection = new StubSelection(); // 不应被调用
        ResolveChannelRouteUseCase uc = newUseCase(rules, cache, selection);

        ChannelRouteDecision d = uc.resolveFirst("gpt-4o", "/v1/responses", "g1",
                null, false, 3, new MapRequestContext(Map.of("prompt_cache_key", "sess1")), NOW);

        assertTrue(d.stickyHit(), "应为粘连命中");
        assertEquals(42L, d.channelId());
        assertTrue(d.skipRetryOnFailure(), "codex 内置规则 skipRetryOnFailure=true");
        assertTrue(d.passHeaders().containsKey("OpenAI-Beta"), "F-2030 透传 header 带回");
    }

    @Test
    @DisplayName("无亲和 + 普通分组：直选满足渠道")
    void normalGroupSelectsChannel() {
        FakeRuleRepository rules = new FakeRuleRepository(List.of(), AffinitySettings.defaults());
        FakeCacheRepository cache = new FakeCacheRepository();
        StubSelection selection = new StubSelection().put("default", 0, candidate(7L, "default"));
        ResolveChannelRouteUseCase uc = newUseCase(rules, cache, selection);

        ChannelRouteDecision d = uc.resolveFirst("gpt-4o", "/v1/chat/completions", "default",
                null, false, 3, new MapRequestContext(Map.of()), NOW);

        assertFalse(d.stickyHit());
        assertTrue(d.hasChannel());
        assertEquals(7L, d.channelId());
        assertFalse(d.exhausted());
    }

    @Test
    @DisplayName("无亲和 + 普通分组无渠道：耗尽")
    void normalGroupNoChannelExhausted() {
        FakeRuleRepository rules = new FakeRuleRepository(List.of(), AffinitySettings.defaults());
        FakeCacheRepository cache = new FakeCacheRepository();
        StubSelection selection = new StubSelection(); // 无任何渠道
        ResolveChannelRouteUseCase uc = newUseCase(rules, cache, selection);

        ChannelRouteDecision d = uc.resolveFirst("gpt-4o", "/v1/chat/completions", "default",
                null, false, 3, new MapRequestContext(Map.of()), NOW);

        assertFalse(d.hasChannel());
        assertTrue(d.exhausted());
    }

    @Test
    @DisplayName("auto 分组：首组选到渠道")
    void autoGroupSelectsFirstGroup() {
        FakeRuleRepository rules = new FakeRuleRepository(List.of(), AffinitySettings.defaults());
        FakeCacheRepository cache = new FakeCacheRepository();
        StubSelection selection = new StubSelection().put("groupA", 0, candidate(11L, "groupA"));
        ResolveChannelRouteUseCase uc = newUseCase(rules, cache, selection);

        ChannelRouteDecision d = uc.resolveFirst("gpt-4o", "/v1/chat/completions", "auto",
                List.of("groupA", "groupB"), false, 3, new MapRequestContext(Map.of()), NOW);

        assertTrue(d.hasChannel());
        assertEquals(11L, d.channelId());
        assertFalse(d.exhausted());
    }

    @Test
    @DisplayName("auto 分组：全组无渠道则耗尽")
    void autoGroupAllExhausted() {
        FakeRuleRepository rules = new FakeRuleRepository(List.of(), AffinitySettings.defaults());
        FakeCacheRepository cache = new FakeCacheRepository();
        StubSelection selection = new StubSelection(); // 所有组所有层均无渠道
        ResolveChannelRouteUseCase uc = newUseCase(rules, cache, selection);

        ChannelRouteDecision d = uc.resolveFirst("gpt-4o", "/v1/chat/completions", "auto",
                List.of("groupA", "groupB"), false, 0, new MapRequestContext(Map.of()), NOW);

        assertFalse(d.hasChannel());
        assertTrue(d.exhausted());
    }

    @Test
    @DisplayName("F-2034 skipRetryOnFailure：重试步直接耗尽")
    void skipRetryOnFailureTerminatesRetry() {
        FakeRuleRepository rules = new FakeRuleRepository(
                List.of(AffinityRule.builtinClaude()), AffinitySettings.defaults());
        FakeCacheRepository cache = new FakeCacheRepository();
        // 命中 claude 规则但无粘连（缓存空），首步走选渠选到渠道。
        StubSelection selection = new StubSelection().put("g1", 0, candidate(5L, "g1"));
        ResolveChannelRouteUseCase uc = newUseCase(rules, cache, selection);

        ChannelRouteDecision first = uc.resolveFirst("claude-3-opus", "/v1/messages", "g1",
                null, false, 3, new MapRequestContext(Map.of("metadata.user_id", "u_1")), NOW);
        assertTrue(first.hasChannel());
        assertTrue(first.skipRetryOnFailure(), "claude 内置规则 skipRetryOnFailure=true");

        // 重试步：因 skipRetryOnFailure=true 直接耗尽，不再选渠。
        ChannelRouteDecision retry = uc.resolveRetry(first, null);
        assertFalse(retry.hasChannel());
        assertTrue(retry.exhausted());
    }

    @Test
    @DisplayName("F-2031 onSuccess：switchOnSuccess=true 回写缓存")
    void onSuccessWritesCache() {
        FakeRuleRepository rules = new FakeRuleRepository(
                List.of(AffinityRule.builtinCodex()), AffinitySettings.defaults());
        FakeCacheRepository cache = new FakeCacheRepository();
        StubSelection selection = new StubSelection().put("g1", 0, candidate(9L, "g1"));
        ResolveChannelRouteUseCase uc = newUseCase(rules, cache, selection);

        ChannelRouteDecision first = uc.resolveFirst("gpt-4o", "/v1/responses", "g1",
                null, false, 3, new MapRequestContext(Map.of("prompt_cache_key", "sess9")), NOW);
        assertTrue(first.hasChannel());
        assertEquals(9L, first.channelId());

        // 成功回写：会话键→渠道 9 应进缓存。
        uc.onSuccess(first.affinityDecision(), 9L, NOW);

        AffinityCacheKey key = new AffinityCacheKey("codex", "sess9", "g1");
        Optional<AffinityCacheEntry> entry = cache.find(key);
        assertTrue(entry.isPresent(), "成功后应回写缓存");
        assertEquals(9L, entry.get().channelId());
    }
}
