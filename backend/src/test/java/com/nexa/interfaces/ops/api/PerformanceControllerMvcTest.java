package com.nexa.interfaces.ops.api;

import com.nexa.application.ops.performance.CleanDiskCacheUseCase;
import com.nexa.application.ops.performance.GetPerformanceStatsUseCase;
import com.nexa.application.ops.performance.ResetStatsUseCase;
import com.nexa.application.ops.port.CacheStatsProvider;
import com.nexa.application.ops.port.DiskCacheManager;
import com.nexa.application.ops.port.SystemRuntimeProbe;
import com.nexa.shared.security.domain.rbac.ActorRole;
import com.nexa.shared.security.domain.rbac.AuthenticatedActor;
import com.nexa.shared.security.infrastructure.auth.ActorAuthenticationToken;
import com.nexa.shared.security.interfaces.api.SecurityExceptionHandler;
import com.nexa.shared.security.interfaces.web.RequireRoleInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PerformanceController HTTP 链路 MockMvc 回归（R4-B 系统设置操作端点接线）。
 *
 * <p>standaloneSetup 起真实 MVC dispatch + 装载 {@link RequireRoleInterceptor}（与生产同款方法级
 * 鉴权）+ {@link SecurityExceptionHandler}（401/403 翻译）。链路等价 curl 打到 {@code /api/ops/*}：
 * 拦截器鉴权 → 控制器 → 真实 {@link CleanDiskCacheUseCase}/{@link ResetStatsUseCase}/
 * {@link GetPerformanceStatsUseCase} → 端口桩。</p>
 *
 * <p>用例为真实对象（非 mock），仅最外层端口（{@link DiskCacheManager}/{@link CacheStatsProvider}/
 * {@link SystemRuntimeProbe}）用内存桩计数，验证三条活体语义：admin 200 + 端口真实被调；非 admin 403；
 * performance 出参回显真实磁盘缓存占用字节。</p>
 */
@DisplayName("PerformanceController HTTP 回归 - R4-B 缓存清理/重置统计/性能查询")
class PerformanceControllerMvcTest {

    private MockMvc mockMvc;
    private AtomicInteger cleanupCalls;
    private AtomicInteger resetCalls;

    @BeforeEach
    void setUp() {
        cleanupCalls = new AtomicInteger();
        resetCalls = new AtomicInteger();

        DiskCacheManager diskCacheManager = new DiskCacheManager() {
            @Override
            public DiskCacheInfo info() {
                return new DiskCacheInfo(true, 12L, 482L * 1024 * 1024);
            }

            @Override
            public CleanupResult cleanupInactive() {
                cleanupCalls.incrementAndGet();
                return new CleanupResult(7L, 123_456L);
            }
        };
        CacheStatsProvider cacheStatsProvider = new CacheStatsProvider() {
            @Override
            public CacheStats sample() {
                return new CacheStats(100L, 20L, 8L);
            }

            @Override
            public void resetStats() {
                resetCalls.incrementAndGet();
            }
        };
        SystemRuntimeProbe systemRuntimeProbe = new SystemRuntimeProbe() {
            @Override
            public RuntimeStats sampleRuntimeStats() {
                return new RuntimeStats(1024L, 2048L, 3L, 16, 1_000_000L, 400_000L);
            }

            @Override
            public void forceGarbageCollection() {
                // 不参与本控制器端点。
            }
        };

        PerformanceController controller = new PerformanceController(
                new GetPerformanceStatsUseCase(systemRuntimeProbe, cacheStatsProvider, diskCacheManager),
                new CleanDiskCacheUseCase(diskCacheManager),
                new ResetStatsUseCase(cacheStatsProvider));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new RequireRoleInterceptor())
                .setControllerAdvice(new SecurityExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(ActorRole role) {
        SecurityContextHolder.getContext().setAuthentication(
                new ActorAuthenticationToken(new AuthenticatedActor(1L, "tester", role)));
    }

    @Test
    @DisplayName("admin POST /api/ops/cache/clear → 200 + 真实调用清理用例 + 回显删除数/释放字节")
    void adminClearCacheReturns200AndInvokesUseCase() throws Exception {
        authenticateAs(ActorRole.ADMIN);
        mockMvc.perform(post("/api/ops/cache/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.deleted_count", is(7)))
                .andExpect(jsonPath("$.data.freed_bytes", is(123456)));
        assertEquals(1, cleanupCalls.get(), "清理用例须真实落到磁盘缓存端口");
    }

    @Test
    @DisplayName("admin POST /api/ops/stats/reset → 200 + 真实调用重置用例")
    void adminResetStatsReturns200AndInvokesUseCase() throws Exception {
        authenticateAs(ActorRole.ADMIN);
        mockMvc.perform(post("/api/ops/stats/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
        assertEquals(1, resetCalls.get(), "重置用例须真实落到缓存统计端口");
    }

    @Test
    @DisplayName("admin GET /api/ops/performance → 200 + 回显真实磁盘缓存占用字节")
    void adminGetPerformanceReturns200WithRealCacheUsage() throws Exception {
        authenticateAs(ActorRole.ADMIN);
        long expectedBytes = 482L * 1024 * 1024;
        mockMvc.perform(get("/api/ops/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.disk_cache_info.total_bytes", is((int) expectedBytes)))
                .andExpect(jsonPath("$.data.disk_cache_info.enabled", is(true)));
    }

    @Test
    @DisplayName("非 admin（common）POST /api/ops/cache/clear → 403 且不触达用例")
    void nonAdminClearCacheReturns403() throws Exception {
        authenticateAs(ActorRole.COMMON);
        mockMvc.perform(post("/api/ops/cache/clear"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
        assertEquals(0, cleanupCalls.get(), "越权请求绝不得触达清理用例");
    }

    @Test
    @DisplayName("非 admin（common）POST /api/ops/stats/reset → 403 且不触达用例")
    void nonAdminResetStatsReturns403() throws Exception {
        authenticateAs(ActorRole.COMMON);
        mockMvc.perform(post("/api/ops/stats/reset"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
        assertEquals(0, resetCalls.get(), "越权请求绝不得触达重置用例");
    }
}
