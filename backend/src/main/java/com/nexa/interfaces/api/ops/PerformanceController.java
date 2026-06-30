package com.nexa.interfaces.api.ops;

import com.nexa.application.ops.performance.CleanDiskCacheUseCase;
import com.nexa.application.ops.performance.GetPerformanceStatsUseCase;
import com.nexa.application.ops.performance.ResetStatsUseCase;
import com.nexa.interfaces.api.ops.dto.DiskCacheCleanupVO;
import com.nexa.interfaces.api.ops.dto.PerformanceStatsVO;
import com.nexa.common.security.rbac.AuthLevel;
import com.nexa.common.security.annotation.RequireRole;
import com.nexa.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统性能运维控制器（AdminAuth 端点，接口层，F-4019/F-4020/F-4021）。
 *
 * <p>承载系统设置页「高级」区的运维操作端点（对齐 API-ENDPOINTS §9.3 性能统计/缓存/统计）：
 * <ul>
 *   <li>{@code GET  /api/ops/performance} —— F-4019 性能统计快照（缓存命中/内存/磁盘缓存占用）</li>
 *   <li>{@code POST /api/ops/cache/clear} —— F-4020 清理不活跃磁盘缓存（保护进行中请求）</li>
 *   <li>{@code POST /api/ops/stats/reset} —— F-4021 重置缓存命中统计计数（幂等）</li>
 * </ul>
 * </p>
 *
 * <p><b>鉴权（安全声明）</b>：运维操作要求 AdminAuth（与 {@code AffinityCacheController} 同口径，
 * 缓存运维属管理面动作）。类级 {@link RequireRole}({@link AuthLevel#ADMIN}) 由 {@code RequireRoleInterceptor}
 * 统一拦截：未达 admin → 403、未认证 → 401（{@code SecurityExceptionHandler} 兜底）。root 满足 admin。</p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（调用例 → 裁剪视图）。编排在应用层用例，无业务规则。</p>
 */
@RestController
@RequestMapping("/api/ops")
@RequireRole(AuthLevel.ADMIN)
public class PerformanceController {

    private final GetPerformanceStatsUseCase getPerformanceStatsUseCase;
    private final CleanDiskCacheUseCase cleanDiskCacheUseCase;
    private final ResetStatsUseCase resetStatsUseCase;

    /**
     * @param getPerformanceStatsUseCase 性能统计查询用例
     * @param cleanDiskCacheUseCase      磁盘缓存清理用例
     * @param resetStatsUseCase          统计重置用例
     */
    public PerformanceController(GetPerformanceStatsUseCase getPerformanceStatsUseCase,
                                 CleanDiskCacheUseCase cleanDiskCacheUseCase,
                                 ResetStatsUseCase resetStatsUseCase) {
        this.getPerformanceStatsUseCase = getPerformanceStatsUseCase;
        this.cleanDiskCacheUseCase = cleanDiskCacheUseCase;
        this.resetStatsUseCase = resetStatsUseCase;
    }

    /**
     * 性能统计查询（F-4019）。
     *
     * @return {@code data} = 性能统计视图（含 disk_cache_info.total_bytes 真实缓存占用）
     */
    @GetMapping("/performance")
    public ApiResponse<PerformanceStatsVO> performance() {
        return ApiResponse.okData(PerformanceStatsVO.from(getPerformanceStatsUseCase.execute()));
    }

    /**
     * 清空磁盘缓存（F-4020，清理不活跃缓存文件，保护进行中请求）。
     *
     * @return {@code data} = 清理结果（删除数 + 释放字节）
     */
    @PostMapping("/cache/clear")
    public ApiResponse<DiskCacheCleanupVO> clearCache() {
        return ApiResponse.okData(DiskCacheCleanupVO.from(cleanDiskCacheUseCase.execute()));
    }

    /**
     * 重置统计（F-4021，重置缓存命中/未命中计数，幂等）。
     *
     * @return 成功回执
     */
    @PostMapping("/stats/reset")
    public ApiResponse<Void> resetStats() {
        resetStatsUseCase.execute();
        return ApiResponse.ok();
    }
}
