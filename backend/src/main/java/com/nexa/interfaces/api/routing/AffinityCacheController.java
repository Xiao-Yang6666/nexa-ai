package com.nexa.interfaces.api.routing;

import com.nexa.application.routing.ClearAffinityCacheUseCase;
import com.nexa.application.routing.QueryAffinityUsageUseCase;
import com.nexa.common.web.ApiResponse;
import com.nexa.common.security.rbac.AuthLevel;
import com.nexa.common.security.annotation.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 渠道亲和缓存运维控制器（AdminAuth 端点，接口层，F-2032 清空 / F-2033 用量统计）。
 *
 * <p>承载运维端点（对齐 openapi /api/channel_affinity_cache/clear + /api/log/channel_affinity_usage_cache）：
 * <ul>
 *   <li>{@code POST /api/channel_affinity_cache/clear} —— F-2032 清空缓存（全部或按规则）</li>
 *   <li>{@code GET  /api/log/channel_affinity_usage_cache} —— F-2033 用量统计查询</li>
 * </ul>
 * 鉴权：AdminAuth（对齐 openapi schema）。接口层薄翻译，业务逻辑在 domain/application。
 * 异常处理委托 {@link AffinityCacheExceptionHandler}（统一映射 domain exception → HTTP 状态码）。</p>
 */
@RestController
@RequestMapping("/api")
@RequireRole(AuthLevel.ADMIN)
public class AffinityCacheController {

    private final ClearAffinityCacheUseCase clearUseCase;
    private final QueryAffinityUsageUseCase queryUsageUseCase;

    /**
     * @param clearUseCase      清空缓存用例
     * @param queryUsageUseCase 用量统计查询用例
     */
    public AffinityCacheController(ClearAffinityCacheUseCase clearUseCase,
                                  QueryAffinityUsageUseCase queryUsageUseCase) {
        this.clearUseCase = clearUseCase;
        this.queryUsageUseCase = queryUsageUseCase;
    }

    /**
     * 清空亲和缓存（POST /api/channel_affinity_cache/clear，F-2032）。
     *
     * <p>入参：{@code {all:true}} 全清；{@code {rule_name:"codex"}} 按规则清；二者必须有且仅有一个。
     * 响应：{@code {success:true, data:{deleted:N}}}。</p>
     *
     * @param request 清空请求体（对齐 openapi additionalProperties 但明确两字段）
     * @return 删除条数
     */
    @PostMapping("/channel_affinity_cache/clear")
    public ApiResponse<Map<String, Object>> clear(@RequestBody Map<String, Object> request) {
        Boolean all = request.containsKey("all") ? (Boolean) request.get("all") : null;
        String ruleName = (String) request.get("rule_name");
        long deleted = clearUseCase.execute(all, ruleName);
        return ApiResponse.okData(Map.of("deleted", deleted));
    }

    /**
     * 用量统计查询（GET /api/log/channel_affinity_usage_cache，F-2033 + F-4014）。
     *
     * <p>入参：{@code rule_name}（必填）、{@code key_fp}（必填）、{@code using_group}（可选）。
     * 响应：命中返回 {@code {success:true, data:{channel_id, hit_count, last_hit_at, expires_at}}}，
     * 未命中返回 {@code data:null}（AdminView additionalProperties:true）。</p>
     *
     * @param ruleName   规则名（必填）
     * @param keyFp      会话键指纹（必填，前端按同算法计算 SHA-256 前 16 字节 hex）
     * @param usingGroup 使用分组（可选）
     * @return 用量统计 Map（未命中返回 data=null）
     */
    @GetMapping("/log/channel_affinity_usage_cache")
    public ApiResponse<Map<String, Object>> queryUsage(@RequestParam("rule_name") String ruleName,
                                                       @RequestParam("key_fp") String keyFp,
                                                       @RequestParam(value = "using_group", required = false) String usingGroup) {
        return queryUsageUseCase.execute(ruleName, keyFp, usingGroup)
                .map(ApiResponse::okData)
                .orElseGet(() -> ApiResponse.okData(null));
    }
}
