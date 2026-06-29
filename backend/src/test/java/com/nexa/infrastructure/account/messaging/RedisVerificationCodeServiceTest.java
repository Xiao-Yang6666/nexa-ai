package com.nexa.infrastructure.account.messaging;

import com.nexa.domain.account.vo.Email;
import com.nexa.domain.account.vo.VerificationCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;

/**
 * {@link RedisVerificationCodeService} 单测：mock {@link StringRedisTemplate}，不依赖真实 Redis。
 * 覆盖原生 TTL 写入、key 前缀、一次性消费、过期/不匹配不消费、Redis 故障降级回内存。
 */
class RedisVerificationCodeServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private InMemoryVerificationCodeService fallback;
    private RedisVerificationCodeService service;

    private final Email email = Email.of("user@example.com");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        fallback = new InMemoryVerificationCodeService();
        service = new RedisVerificationCodeService(redis, fallback);
    }

    @Test
    void issue_writesWithBusinessPrefixAndNativeTtl() {
        VerificationCode code = service.issue(email);

        verify(ops).set(eq("verifycode:user@example.com"), eq(code.value()), eq(Duration.ofMinutes(10)));
    }

    @Test
    void verifyAndConsume_matchingCode_returnsTrueAndDeletesKey() {
        when(ops.get("verifycode:user@example.com")).thenReturn("123456");

        boolean ok = service.verifyAndConsume(email, VerificationCode.of("123456"));

        assertThat(ok).isTrue();
        verify(redis).delete("verifycode:user@example.com"); // 一次性消费
    }

    @Test
    void verifyAndConsume_noStoredCode_returnsFalse() {
        when(ops.get(anyString())).thenReturn(null);

        boolean ok = service.verifyAndConsume(email, VerificationCode.of("123456"));

        assertThat(ok).isFalse();
        verify(redis, never()).delete(anyString()); // 失败不消费
    }

    @Test
    void verifyAndConsume_wrongCode_returnsFalseAndKeepsKey() {
        when(ops.get("verifycode:user@example.com")).thenReturn("999999");

        boolean ok = service.verifyAndConsume(email, VerificationCode.of("123456"));

        assertThat(ok).isFalse();
        verify(redis, never()).delete(anyString()); // 不匹配不消费，用户可重试
    }

    @Test
    void verifyAndConsume_nullCode_returnsFalseWithoutTouchingRedis() {
        boolean ok = service.verifyAndConsume(email, null);

        assertThat(ok).isFalse();
        verify(ops, never()).get(anyString());
    }

    @Test
    void issue_redisDown_degradesToMemoryFallback() {
        doThrow(new RuntimeException("connection refused"))
                .when(ops).set(anyString(), anyString(), any(Duration.class));

        VerificationCode code = service.issue(email);

        // 降级后内存里能校验通过，说明确实落到了 fallback。
        assertThat(fallback.verifyAndConsume(email, code)).isTrue();
    }

    @Test
    void verifyAndConsume_redisGetDown_degradesToMemoryFallback() {
        // 先在内存兜底里发一枚码。
        VerificationCode code = fallback.issue(email);
        when(ops.get(anyString())).thenThrow(new RuntimeException("connection refused"));

        boolean ok = service.verifyAndConsume(email, code);

        assertThat(ok).isTrue();
    }

    @Test
    void verifyAndConsume_evictFailureIgnored_stillReturnsTrue() {
        when(ops.get("verifycode:user@example.com")).thenReturn("123456");
        when(redis.delete(anyString())).thenThrow(new RuntimeException("evict failed"));

        boolean ok = service.verifyAndConsume(email, VerificationCode.of("123456"));

        assertThat(ok).isTrue(); // 删除失败不影响校验结果，TTL 兜底回收
        verify(redis, times(1)).delete("verifycode:user@example.com");
    }
}
