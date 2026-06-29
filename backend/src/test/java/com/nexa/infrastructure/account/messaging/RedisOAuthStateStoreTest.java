package com.nexa.infrastructure.account.messaging;

import com.nexa.domain.account.vo.OAuthState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/**
 * {@link RedisOAuthStateStore} 单测：mock {@link StringRedisTemplate}，不依赖真实 Redis。
 * 覆盖 key 前缀 + 原生 TTL 写入、aff 暂存、getAndDelete 一次性消费、过期/未命中返回空、Redis 降级。
 */
class RedisOAuthStateStoreTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private InMemoryOAuthStateStore fallback;
    private RedisOAuthStateStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        fallback = new InMemoryOAuthStateStore();
        store = new RedisOAuthStateStore(redis, fallback);
    }

    @Test
    void save_writesWithPrefixAndTtl_affAsValue() {
        OAuthState state = OAuthState.generate("INV123");

        store.save(state);

        verify(ops).set(eq("oauthstate:" + state.token()), eq("INV123"), eq(Duration.ofMinutes(10)));
    }

    @Test
    void save_nullAff_storedAsEmptyString() {
        OAuthState state = OAuthState.generate(null);

        store.save(state);

        verify(ops).set(eq("oauthstate:" + state.token()), eq(""), eq(Duration.ofMinutes(10)));
    }

    @Test
    void consume_hit_returnsStateWithAffAndIsOneTime() {
        when(ops.getAndDelete("oauthstate:tok1")).thenReturn("INV123");

        Optional<OAuthState> result = store.consume("tok1");

        assertThat(result).isPresent();
        assertThat(result.get().token()).isEqualTo("tok1");
        assertThat(result.get().aff()).isEqualTo("INV123");
    }

    @Test
    void consume_emptyAffValue_yieldsNullAff() {
        when(ops.getAndDelete("oauthstate:tok1")).thenReturn("");

        Optional<OAuthState> result = store.consume("tok1");

        assertThat(result).isPresent();
        assertThat(result.get().aff()).isNull();
    }

    @Test
    void consume_miss_returnsEmpty() {
        when(ops.getAndDelete(anyString())).thenReturn(null);

        assertThat(store.consume("tok1")).isEmpty();
    }

    @Test
    void consume_blankToken_returnsEmptyWithoutTouchingRedis() {
        assertThat(store.consume("  ")).isEmpty();
        verify(ops, never()).getAndDelete(anyString());
    }

    @Test
    void save_redisDown_degradesToMemoryFallback() {
        OAuthState state = OAuthState.generate("INV123");
        doThrow(new RuntimeException("connection refused"))
                .when(ops).set(anyString(), anyString(), eq(Duration.ofMinutes(10)));

        store.save(state);

        // 降级后内存兜底里能 consume 回来。
        assertThat(fallback.consume(state.token())).isPresent();
    }

    @Test
    void consume_redisDown_degradesToMemoryFallback() {
        OAuthState state = OAuthState.generate("INV123");
        fallback.save(state);
        when(ops.getAndDelete(anyString())).thenThrow(new RuntimeException("connection refused"));

        Optional<OAuthState> result = store.consume(state.token());

        assertThat(result).isPresent();
        assertThat(result.get().aff()).isEqualTo("INV123");
    }
}
