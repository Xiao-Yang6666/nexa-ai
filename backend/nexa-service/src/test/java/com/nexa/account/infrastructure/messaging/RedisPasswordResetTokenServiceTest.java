package com.nexa.account.infrastructure.messaging;

import com.nexa.account.domain.vo.Email;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/**
 * {@link RedisPasswordResetTokenService} 单测：mock {@link StringRedisTemplate}，不依赖真实 Redis。
 * 覆盖 token/email 双 key 写入与原生 TTL、双因子校验、一次性消费、同邮箱重发作废旧令牌、Redis 降级。
 */
class RedisPasswordResetTokenServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private InMemoryPasswordResetTokenService fallback;
    private RedisPasswordResetTokenService service;

    private final Email email = Email.of("user@example.com");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        fallback = new InMemoryPasswordResetTokenService();
        service = new RedisPasswordResetTokenService(redis, fallback);
    }

    @Test
    void issue_writesTokenAndEmailKeysWithTtl() {
        String token = service.issue(email);

        verify(ops).set(eq("pwdreset:token:" + token), eq("user@example.com"), eq(Duration.ofMinutes(30)));
        verify(ops).set(eq("pwdreset:email:user@example.com"), eq(token), eq(Duration.ofMinutes(30)));
    }

    @Test
    void issue_sameEmail_revokesOldToken() {
        when(ops.get("pwdreset:email:user@example.com")).thenReturn("old-token");

        service.issue(email);

        verify(redis).delete("pwdreset:token:old-token"); // 旧令牌作废，以最新为准
    }

    @Test
    void verifyAndConsume_validTokenBoundToEmail_returnsEmailAndDeletesBothKeys() {
        when(ops.get("pwdreset:token:tok123")).thenReturn("user@example.com");

        Optional<Email> result = service.verifyAndConsume(email, "tok123");

        assertThat(result).contains(email);
        verify(redis).delete("pwdreset:token:tok123");
        verify(redis).delete("pwdreset:email:user@example.com");
    }

    @Test
    void verifyAndConsume_unknownToken_returnsEmpty() {
        when(ops.get(anyString())).thenReturn(null);

        assertThat(service.verifyAndConsume(email, "nope")).isEmpty();
        verify(redis, never()).delete(anyString());
    }

    @Test
    void verifyAndConsume_tokenBoundToDifferentEmail_returnsEmpty() {
        when(ops.get("pwdreset:token:tok123")).thenReturn("other@example.com");

        // 双因子：令牌绑定 other@，用 user@ 提交应失败（防用 A 令牌重置 B 密码）。
        assertThat(service.verifyAndConsume(email, "tok123")).isEmpty();
        verify(redis, never()).delete(anyString());
    }

    @Test
    void verifyAndConsume_blankToken_returnsEmptyWithoutTouchingRedis() {
        assertThat(service.verifyAndConsume(email, "  ")).isEmpty();
        verify(ops, never()).get(anyString());
    }

    @Test
    void issue_redisDown_degradesToMemoryFallback() {
        doThrow(new RuntimeException("connection refused"))
                .when(ops).set(anyString(), anyString(), any(Duration.class));

        String token = service.issue(email);

        // 降级后内存兜底里该令牌可校验通过。
        assertThat(fallback.verifyAndConsume(email, token)).contains(email);
    }

    @Test
    void verifyAndConsume_redisDown_degradesToMemoryFallback() {
        String token = fallback.issue(email);
        when(ops.get(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertThat(service.verifyAndConsume(email, token)).contains(email);
    }
}
