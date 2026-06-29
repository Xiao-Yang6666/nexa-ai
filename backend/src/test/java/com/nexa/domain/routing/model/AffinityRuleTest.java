package com.nexa.domain.routing.model;

import com.nexa.domain.routing.exception.InvalidAffinityParameterException;
import com.nexa.domain.routing.vo.AffinityRequestContext;
import com.nexa.domain.routing.vo.KeySource;
import com.nexa.domain.routing.vo.KeySourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AffinityRule} 聚合根单测（纯 JUnit，零 Spring/DB）。
 *
 * <p>覆盖 F-2029 会话键提取、F-2030 header 透传、F-2034 SkipRetryOnFailure、命中判定（PRD CH-4），
 * 按正常/边界/异常组织（backend-engineer §3.3）。</p>
 */
@DisplayName("AffinityRule 亲和规则聚合根")
class AffinityRuleTest {

    /** 简单 mock 请求上下文：从 Map 提供 gjson/header/context 取值。 */
    private static AffinityRequestContext ctx(Map<String, String> json, Map<String, String> headers) {
        return new AffinityRequestContext() {
            @Override
            public String readJsonPath(String jsonPath) {
                return json.get(jsonPath);
            }

            @Override
            public String readHeader(String headerName) {
                return headers.get(headerName);
            }

            @Override
            public Optional<Integer> readContextInt(String key) {
                return Optional.empty();
            }

            @Override
            public Optional<String> readContextString(String key) {
                return Optional.empty();
            }
        };
    }

    @Test
    @DisplayName("内置 codex 规则：gpt-* + /v1/responses 命中，skipRetryOnFailure=true，透传 OpenAI-Beta")
    void builtinCodex() {
        AffinityRule codex = AffinityRule.builtinCodex();
        assertTrue(codex.builtIn());
        assertTrue(codex.skipRetryOnFailure());
        assertTrue(codex.matches("gpt-4o", "/v1/responses"));
        assertFalse(codex.matches("claude-3", "/v1/responses"));
        assertFalse(codex.matches("gpt-4o", "/v1/chat/completions"));
        assertEquals("responses=experimental", codex.passHeaders().get("OpenAI-Beta"));
    }

    @Test
    @DisplayName("内置 claude 规则：claude-* + /v1/messages 命中，提取 metadata.user_id 会话键")
    void builtinClaude() {
        AffinityRule claude = AffinityRule.builtinClaude();
        assertTrue(claude.matches("claude-3-5-sonnet", "/v1/messages"));
        Map<String, String> json = new HashMap<>();
        json.put("metadata.user_id", "u_42");
        String key = claude.extractKey(ctx(json, Map.of()));
        assertEquals("u_42", key);
    }

    @Test
    @DisplayName("F-2029 多 key_sources 会话键按 | 拼接")
    void extractKeyMultiSource() {
        AffinityRule rule = AffinityRule.custom("multi", true, "^gpt-.*", "^/v1/responses$",
                List.of(new KeySource(KeySourceType.GJSON, "prompt_cache_key"),
                        new KeySource(KeySourceType.REQUEST_HEADER, "X-Session")),
                Map.of(), false, 0L);
        Map<String, String> json = new HashMap<>();
        json.put("prompt_cache_key", "abc");
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Session", "sess1");
        assertEquals("abc|sess1", rule.extractKey(ctx(json, headers)));
    }

    @Test
    @DisplayName("F-2029 任一 key_source 缺值 → 键提取失败返回 null")
    void extractKeyMissingSourceReturnsNull() {
        AffinityRule rule = AffinityRule.custom("multi", true, "^gpt-.*", "^/v1/responses$",
                List.of(new KeySource(KeySourceType.GJSON, "prompt_cache_key"),
                        new KeySource(KeySourceType.REQUEST_HEADER, "X-Session")),
                Map.of(), false, 0L);
        Map<String, String> json = new HashMap<>();
        json.put("prompt_cache_key", "abc"); // header X-Session 缺失
        assertNull(rule.extractKey(ctx(json, Map.of())));
    }

    @Test
    @DisplayName("规则禁用后 matches 恒为 false（PRD CH-4 §2 总开关/规则开关前置）")
    void disabledRuleNeverMatches() {
        AffinityRule rule = AffinityRule.custom("r", false, "^gpt-.*", "^/v1/responses$",
                List.of(new KeySource(KeySourceType.GJSON, "k")), Map.of(), false, 0L);
        assertFalse(rule.matches("gpt-4o", "/v1/responses"));
        rule.setEnabled(true);
        assertTrue(rule.matches("gpt-4o", "/v1/responses"));
    }

    @Test
    @DisplayName("F-2031 effectiveTtlSeconds：规则 ttl>0 覆盖默认，否则回落默认（至少 1）")
    void effectiveTtl() {
        AffinityRule withTtl = AffinityRule.custom("a", true, "^gpt-.*", "^/v1/responses$",
                List.of(new KeySource(KeySourceType.GJSON, "k")), Map.of(), false, 120L);
        assertEquals(120L, withTtl.effectiveTtlSeconds(3600L));
        AffinityRule noTtl = AffinityRule.custom("b", true, "^gpt-.*", "^/v1/responses$",
                List.of(new KeySource(KeySourceType.GJSON, "k")), Map.of(), false, 0L);
        assertEquals(3600L, noTtl.effectiveTtlSeconds(3600L));
        assertEquals(1L, noTtl.effectiveTtlSeconds(0L));
    }

    @Test
    @DisplayName("非法正则 → 创建抛 InvalidAffinityParameterException")
    void invalidRegexRejected() {
        assertThrows(InvalidAffinityParameterException.class, () ->
                AffinityRule.custom("bad", true, "[unclosed", "^/v1/responses$",
                        List.of(new KeySource(KeySourceType.GJSON, "k")), Map.of(), false, 0L));
    }

    @Test
    @DisplayName("空 key_sources → 创建抛异常")
    void emptyKeySourcesRejected() {
        assertThrows(InvalidAffinityParameterException.class, () ->
                AffinityRule.custom("k", true, "^gpt-.*", "^/v1/responses$",
                        List.of(), Map.of(), false, 0L));
    }

    @Test
    @DisplayName("内置规则 update 不改命中条件/key_sources，仅 enabled/headers/ttl 可变")
    void builtinUpdateKeepsCoreSemantics() {
        AffinityRule codex = AffinityRule.builtinCodex();
        codex.update(false, "^anything$", "^/x$", List.of(new KeySource(KeySourceType.GJSON, "other")),
                Map.of("H", "v"), false, 99L);
        assertFalse(codex.enabled());
        assertEquals("^gpt-.*", codex.modelRegex()); // 未被改
        assertEquals("^/v1/responses$", codex.pathRegex()); // 未被改
        assertEquals("prompt_cache_key", codex.keySources().get(0).path()); // 未被改
        assertEquals(99L, codex.ttlSeconds()); // 可改
        assertEquals("v", codex.passHeaders().get("H")); // 可改
    }
}
