package com.nexa.infrastructure.token.persistence;

import com.nexa.domain.token.model.Token;
import com.nexa.domain.token.repository.TokenRepository;
import com.nexa.domain.token.vo.TokenStatus;
import com.nexa.infrastructure.token.config.AuthCacheConfig;
import com.nexa.infrastructure.token.persistence.entity.TokenJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 鉴权缓存注解行为单测（T12/CR-05）：验证 {@code @Cacheable findByKey} 命中与 {@code @CacheEvict evictAuthCache} 失效。
 *
 * <p>用轻量 Spring 上下文（{@link EnableCaching} + {@link ConcurrentMapCacheManager} 内存缓存，<b>不依赖真实
 * Redis</b>，CI 必跑）装配真实 {@link TokenRepositoryImpl}（注解切面只对 Spring 管理的代理生效），底层
 * {@link SpringDataTokenJpaRepository} 用 mock 桩。以 mock 的调用次数证明缓存命中（第二次不再查库）与
 * evict 后回源（再查库一次）。线上由 RedisCacheManager 承载同一套注解语义。</p>
 */
@SpringJUnitConfig
class TokenAuthCacheTest {

    private static final String KEY = "sk-cache-test-key";

    @Configuration
    @EnableCaching
    @Import(TokenRepositoryImpl.class)
    static class TestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(AuthCacheConfig.API_KEY_AUTH_CACHE);
        }
    }

    @MockBean
    private SpringDataTokenJpaRepository jpa;

    @Autowired
    private TokenRepository repository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        cacheManager.getCache(AuthCacheConfig.API_KEY_AUTH_CACHE).clear();
    }

    @Test
    void findByKey_secondCall_hitsCacheAndSkipsDb() {
        when(jpa.findByKey(KEY)).thenReturn(Optional.of(entity()));

        Optional<Token> first = repository.findByKey(KEY);
        Optional<Token> second = repository.findByKey(KEY);

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals(KEY, second.get().key());
        // 命中缓存：两次调用只查库一次。
        verify(jpa, times(1)).findByKey(KEY);
        assertNotNull(cacheManager.getCache(AuthCacheConfig.API_KEY_AUTH_CACHE).get(KEY));
    }

    @Test
    void evictAuthCache_invalidatesEntry_nextCallReloadsFromDb() {
        when(jpa.findByKey(KEY)).thenReturn(Optional.of(entity()));

        repository.findByKey(KEY);                 // 查库 + 写缓存
        repository.evictAuthCache(KEY);            // 写穿失效（禁用/删除路径调用）
        repository.findByKey(KEY);                 // 缓存已清 → 再次回源查库

        // evict 后回源：共查库两次（命中场景应只一次）。
        verify(jpa, times(2)).findByKey(KEY);
    }

    private static TokenJpaEntity entity() {
        TokenJpaEntity e = new TokenJpaEntity();
        e.setId(1L);
        e.setUserId(42);
        e.setKey(KEY);
        e.setStatus(TokenStatus.ENABLED.code());
        e.setName("cache-test");
        e.setExpiredTime(Token.NEVER_EXPIRE);
        e.setRemainQuota(100L);
        e.setUnlimitedQuota(false);
        e.setAllowIps("");
        e.setGroup("");
        e.setEndpointLimits("");
        return e;
    }
}
