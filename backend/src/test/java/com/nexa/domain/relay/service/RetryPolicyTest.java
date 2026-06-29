package com.nexa.domain.relay.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RetryPolicy 单元测试（RL-3 ShouldRetryByStatusCode + 渠道自动禁用判定）。
 */
class RetryPolicyTest {

    @Test
    void serverErrorsAreRetryable() {
        assertTrue(RetryPolicy.shouldRetry(500));
        assertTrue(RetryPolicy.shouldRetry(502));
        assertTrue(RetryPolicy.shouldRetry(503));
        assertTrue(RetryPolicy.shouldRetry(504));
        assertTrue(RetryPolicy.shouldRetry(429));
        assertTrue(RetryPolicy.shouldRetry(408));
    }

    @Test
    void clientErrorsAreNotRetryable() {
        assertFalse(RetryPolicy.shouldRetry(400));
        assertFalse(RetryPolicy.shouldRetry(401));
        assertFalse(RetryPolicy.shouldRetry(403));
        assertFalse(RetryPolicy.shouldRetry(404));
        assertFalse(RetryPolicy.shouldRetry(422));
    }

    @Test
    void unknownCodeIsNotRetryableByDefault() {
        assertFalse(RetryPolicy.shouldRetry(418));  // teapot - 保守不重试
    }

    @Test
    void autoDisableOnlyWhenAutoBanEnabled() {
        // RL-3: AutoBan=1 且 401/403 才自动禁用
        assertTrue(RetryPolicy.shouldAutoDisable(1, 401));
        assertTrue(RetryPolicy.shouldAutoDisable(1, 403));
        // AutoBan=0 → 不禁用
        assertFalse(RetryPolicy.shouldAutoDisable(0, 401));
        // 429 限流不禁用（避免误伤）
        assertFalse(RetryPolicy.shouldAutoDisable(1, 429));
        assertFalse(RetryPolicy.shouldAutoDisable(1, 500));
    }
}
