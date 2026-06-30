package com.nexa.interfaces.api.log;

import com.nexa.application.log.QueryProfitDashboardUseCase;
import com.nexa.common.web.ApiResponse;
import com.nexa.interfaces.api.log.dto.ProfitDashboardItemVO;
import com.nexa.interfaces.api.log.dto.ProfitDashboardVO;
import com.nexa.common.security.rbac.AuthLevel;
import com.nexa.common.security.annotation.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 利润分析看板控制器（adminAuth 端点，接口层，F-6009 GET /api/profit/dashboard）。
 *
 * <p>对齐 openapi {@code GET /api/profit/dashboard}：按 {@code dimension}（model/channel/group）维度
 * 聚合 logs 消费记录的售价/成本/利润，产出 {@code ProfitDashboardItem} 数组（包在 {@code data.items}）。
 * 时间区间由 {@code start_timestamp/end_timestamp} 可选限定。</p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（绑定入参 → 调用用例 → 裁剪视图），维度归一/聚合口径/利润率派生全在
 * 领域/应用/基础设施层。维度非法（未知枚举）由领域 {@code InvalidLogQueryException}（→400）经
 * {@link LogExceptionHandler} 翻译。</p>
 *
 * <p><b>鉴权</b>：类级 {@link RequireRole}({@link AuthLevel#ADMIN}) 由 {@code RequireRoleInterceptor}
 * 统一拦截 + {@code SecurityConfig} anyRequest().authenticated() 路径级兜底，未达 admin → 403、
 * 未认证 → 401。root（&ge; admin）可进。管理端全站可见，无 self-scope。</p>
 *
 * <p><b>客户视图铁律</b>：本端点属管理端，输出 AdminView（含成本/利润）；model 维度键为对外公开名 A，
 * 绝不暴露上游模型 B/渠道明细/供应商。客户侧无此端点。</p>
 */
@RestController
@RequestMapping("/api/profit")
@RequireRole(AuthLevel.ADMIN)
public class ProfitController {

    private final QueryProfitDashboardUseCase queryProfitDashboardUseCase;

    /** @param queryProfitDashboardUseCase 利润看板查询用例（F-6009） */
    public ProfitController(QueryProfitDashboardUseCase queryProfitDashboardUseCase) {
        this.queryProfitDashboardUseCase = queryProfitDashboardUseCase;
    }

    /**
     * 利润分析看板（F-6009，{@code GET /api/profit/dashboard}）。
     *
     * @param dimension      聚合维度（{@code model/channel/group}，可空缺省 model；未知→400）
     * @param startTimestamp 起始 epoch 秒（可空=不限）
     * @param endTimestamp   结束 epoch 秒（可空=不限）
     * @return 成功信封，data = { items: ProfitDashboardItem[] }（按利润降序）
     */
    @GetMapping("/dashboard")
    public ApiResponse<ProfitDashboardVO> dashboard(
            @RequestParam(name = "dimension", required = false) String dimension,
            @RequestParam(name = "start_timestamp", required = false) Long startTimestamp,
            @RequestParam(name = "end_timestamp", required = false) Long endTimestamp) {

        List<ProfitDashboardItemVO> items = queryProfitDashboardUseCase
                .query(dimension, startTimestamp, endTimestamp)
                .stream()
                .map(ProfitDashboardItemVO::from)
                .toList();
        return ApiResponse.okData(new ProfitDashboardVO(items));
    }
}
