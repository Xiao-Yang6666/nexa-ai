package com.nexa.observability.domain.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 链路追踪上下文单测（纯 JUnit，F-5012）。
 *
 * <p>覆盖：① generate 产 W3C 合规 traceId(32hex)/spanId(16hex)；② fromTraceParent 续接上游 traceId
 * （贯穿）/坏头降级新建（健壮性）；③ toTraceParent 渲染 W3C 格式（OTel 导出）。</p>
 */
@DisplayName("链路追踪上下文（F-5012）")
class TraceContextTest {

    @DisplayName("generate：traceId 32hex、spanId 16hex、非全 0")
    @Test
    void generateValid() {
        TraceContext t = TraceContext.generate();
        assertTrue(t.traceId().matches("[0-9a-f]{32}"));
        assertTrue(t.spanId().matches("[0-9a-f]{16}"));
        assertNotEquals("00000000000000000000000000000000", t.traceId());
    }

    @DisplayName("generate：两次 traceId 不同（随机）")
    @Test
    void generateUnique() {
        assertNotEquals(TraceContext.generate().traceId(), TraceContext.generate().traceId());
    }

    @DisplayName("toTraceParent：W3C 格式 00-<traceId>-<spanId>-01")
    @Test
    void renderTraceParent() {
        TraceContext t = TraceContext.generate();
        String tp = t.toTraceParent();
        assertEquals("00-" + t.traceId() + "-" + t.spanId() + "-01", tp);
        assertTrue(tp.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01"));
    }

    @DisplayName("fromTraceParent：合法上游头 → 复用 traceId 续接（贯穿），新 spanId")
    @Test
    void continueFromValidParent() {
        TraceContext upstream = TraceContext.generate();
        TraceContext child = TraceContext.fromTraceParent(upstream.toTraceParent());
        // 同一 trace 贯穿：traceId 复用
        assertEquals(upstream.traceId(), child.traceId());
        // 新 span：spanId 不同
        assertNotEquals(upstream.spanId(), child.spanId());
    }

    @DisplayName("fromTraceParent：坏头 / null / 全 0 traceId → 降级新建（不中断追踪）")
    @Test
    void degradeOnBadParent() {
        assertTrue(TraceContext.fromTraceParent(null).traceId().matches("[0-9a-f]{32}"));
        assertTrue(TraceContext.fromTraceParent("garbage").traceId().matches("[0-9a-f]{32}"));
        assertTrue(TraceContext.fromTraceParent("  ").traceId().matches("[0-9a-f]{32}"));
        // 全 0 traceId（非法）→ 新建非全 0
        TraceContext t = TraceContext.fromTraceParent("00-00000000000000000000000000000000-0000000000000000-01");
        assertNotEquals("00000000000000000000000000000000", t.traceId());
    }

    @DisplayName("continueTrace：合法 traceId 复用，非法降级新建")
    @Test
    void continueTraceDirect() {
        String tid = "0123456789abcdef0123456789abcdef";
        assertEquals(tid, TraceContext.continueTrace(tid).traceId());
        assertFalse(TraceContext.continueTrace("short").traceId().equals("short"));
    }
}
