package com.nexa.token.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

/**
 * 鉴权结果缓存配置（T12 / CR-05，命脉路径 /v1/* 每次转发查 key 有效性/归属/限额的热点缓存）。
 *
 * <p>接入方式：Spring Cache 注解式（{@code @Cacheable}/{@code @CacheEvict}），由
 * {@code spring-boot-starter-data-redis} 自动装配的 {@code RedisCacheManager} 承载。本类只做两件事：
 * <ul>
 *   <li>{@link EnableCaching} 开启注解驱动缓存；</li>
 *   <li>用 {@link RedisCacheConfiguration} 兜底 120s TTL（{@code RedisCacheManagerBuilderCustomizer}
 *       不替换自动配置的 manager，仅定制其默认 TTL，避免缓存的禁用/过期 token 长期残留）。</li>
 * </ul></p>
 *
 * <p><b>降级容错（命脉不可挂）</b>：鉴权是转发命脉，Redis 连不上时绝不能阻断转发。覆盖
 * {@link CacheErrorHandler} 把 get/put/evict 的缓存异常吞掉并记 WARN（不重抛）——缓存读失败时
 * Spring 视为未命中，直接回落到被注解方法（直查 DB）；缓存写/清失败也不影响主流程返回。
 * 这是 Spring 现成的容错扩展点，不手写 try-catch 样板。</p>
 */
@Configuration
@EnableCaching
public class AuthCacheConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthCacheConfig.class);

    /** 鉴权缓存名（key 鉴权反查结果，按 apiKey 值缓存）。 */
    public static final String API_KEY_AUTH_CACHE = "apiKeyAuth";

    /** 缓存兜底 TTL：120s（被禁/过期 token 最迟 120s 后随过期失效，写穿 evict 立即失效）。 */
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(120);

    /**
     * 定制自动装配的 {@code RedisCacheManager} 默认 TTL（不重建 manager，只改默认配置）。
     *
     * @return builder 定制器，给所有缓存挂 120s 兜底 TTL
     */
    @Bean
    public org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer redisCacheTtlCustomizer() {
        return builder -> builder.cacheDefaults(
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(DEFAULT_TTL));
    }

    /**
     * Redis 不可用时的容错处理器：吞掉缓存读写/清除异常并记 WARN，保证鉴权降级直查 DB 不被阻断。
     *
     * @return 不重抛缓存异常的 {@link CacheErrorHandler}
     */
    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("auth cache GET failed (degrade to DB), cache={}, key={}: {}",
                        cache.getName(), key, exception.toString());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("auth cache PUT failed (ignored), cache={}, key={}: {}",
                        cache.getName(), key, exception.toString());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("auth cache EVICT failed (ignored), cache={}, key={}: {}",
                        cache.getName(), key, exception.toString());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("auth cache CLEAR failed (ignored), cache={}: {}",
                        cache.getName(), exception.toString());
            }
        };
    }
}
