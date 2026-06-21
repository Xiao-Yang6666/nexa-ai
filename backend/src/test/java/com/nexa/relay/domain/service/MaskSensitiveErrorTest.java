package com.nexa.relay.domain.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MaskSensitiveError 单元测试（RL-3 上游错误脱敏：剥离凭证/URL/主机名 + 截断）。
 */
class MaskSensitiveErrorTest {

    @Test
    void nullDetailFallsBackToStatusSummary() {
        assertEquals("upstream rate limited (429)", MaskSensitiveError.mask(429, null));
        assertEquals("upstream authentication failed (401)", MaskSensitiveError.mask(401, null));
        assertEquals("upstream server error (500)", MaskSensitiveError.mask(500, "   "));
    }

    @Test
    void redactsBearerTokenAndApiKey() {
        String masked = MaskSensitiveError.mask(401,
                "invalid auth: Bearer sk-abc123DEF token rejected, apikey=secret999");
        assertFalse(masked.contains("sk-abc123DEF"), "must not leak bearer token");
        assertFalse(masked.contains("secret999"), "must not leak apikey value");
        assertTrue(masked.contains("[redacted]"));
    }

    @Test
    void redactsUpstreamUrlAndHost() {
        String masked = MaskSensitiveError.mask(502,
                "failed to reach https://api.openai.internal/v1/chat upstream");
        assertFalse(masked.contains("api.openai.internal"), "must not leak upstream host/url");
        assertTrue(masked.startsWith("upstream server error (502)"));
    }

    @Test
    void truncatesOverlongDetail() {
        String longDetail = "x".repeat(1000);
        String masked = MaskSensitiveError.mask(500, longDetail);
        // summary 前缀 + ": " + 截断到 MAX_DETAIL_LENGTH
        assertTrue(masked.length() <= "upstream server error (500): ".length()
                + MaskSensitiveError.MAX_DETAIL_LENGTH);
    }
}
