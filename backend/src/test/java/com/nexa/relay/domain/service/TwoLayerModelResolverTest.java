package com.nexa.relay.domain.service;

import com.nexa.relay.domain.exception.ModelMappingException;
import com.nexa.relay.domain.vo.ModelResolution;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TwoLayerModelResolver 单元测试（RL-7 第②步 C→A→B 两层映射 + 环检测）。
 */
class TwoLayerModelResolverTest {

    @Test
    void identityWhenNoMappingHits() {
        // 任一层未命中则该层恒等（C 未配则 A=C；A 未配底仓则 B=A）
        ModelResolution r = TwoLayerModelResolver.resolve("gpt-4",
                c -> null,
                a -> null);
        assertEquals("gpt-4", r.requested());
        assertEquals("gpt-4", r.resolvedPublic());
        assertEquals("gpt-4", r.upstream());
        assertFalse(r.l1Applied());
        assertFalse(r.l2Applied());
    }

    @Test
    void l1MappingApplied() {
        // 客户层 C→A 命中，超管层未命中 → B=A
        Map<String, String> l1 = Map.of("my-gpt", "gpt-4");
        ModelResolution r = TwoLayerModelResolver.resolve("my-gpt",
                l1::get,
                a -> null);
        assertEquals("my-gpt", r.requested());
        assertEquals("gpt-4", r.resolvedPublic());
        assertEquals("gpt-4", r.upstream());
        assertTrue(r.l1Applied());
        assertFalse(r.l2Applied());
    }

    @Test
    void l2MappingApplied() {
        // 客户层未命中 (A=C)，超管层 A→B 命中
        Map<String, String> l2 = Map.of("gpt-4", "internal-gpt-clone");
        ModelResolution r = TwoLayerModelResolver.resolve("gpt-4",
                c -> null,
                l2::get);
        assertEquals("gpt-4", r.requested());
        assertEquals("gpt-4", r.resolvedPublic());
        assertEquals("internal-gpt-clone", r.upstream());
        assertFalse(r.l1Applied());
        assertTrue(r.l2Applied());
    }

    @Test
    void bothLayersAppliedChained() {
        // C → A (L1) → B (L2)
        Map<String, String> l1 = Map.of("custom-name", "claude-3.5");
        Map<String, String> l2 = Map.of("claude-3.5", "anthropic-internal-id");
        ModelResolution r = TwoLayerModelResolver.resolve("custom-name", l1::get, l2::get);
        assertEquals("custom-name", r.requested());
        assertEquals("claude-3.5", r.resolvedPublic());
        assertEquals("anthropic-internal-id", r.upstream());
        assertTrue(r.l1Applied());
        assertTrue(r.l2Applied());
    }

    @Test
    void cycleDetectionInL1Throws() {
        // L1: a→b→a 成环
        Map<String, String> l1 = Map.of("a", "b", "b", "a");
        assertThrows(ModelMappingException.class,
                () -> TwoLayerModelResolver.resolve("a", l1::get, x -> null));
    }

    @Test
    void emptyModelRejected() {
        assertThrows(ModelMappingException.class,
                () -> TwoLayerModelResolver.resolve("", c -> null, a -> null));
    }
}
