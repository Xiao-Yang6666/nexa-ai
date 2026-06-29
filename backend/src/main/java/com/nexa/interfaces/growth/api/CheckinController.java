package com.nexa.interfaces.growth.api;

import com.nexa.application.growth.DailyCheckinUseCase;
import com.nexa.application.growth.QueryCheckinStatusUseCase;
import com.nexa.domain.growth.vo.CheckinStats;
import com.nexa.shared.web.ApiResponse;
import com.nexa.interfaces.growth.api.dto.CheckinResultView;
import com.nexa.interfaces.growth.api.dto.CheckinStatusView;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.domain.rbac.AuthenticatedActor;
import com.nexa.shared.security.interfaces.annotation.CurrentActor;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 每日签到控制器（接口层，PRD GR-1/GR-2，F-1046/F-1047/F-1048/F-1050）。
 *
 * <p>承载签到端点（对齐 openapi 模块十二「增长（签到）」）：
 * <ul>
 *   <li>{@code POST /api/user/checkin} 每日签到领取随机额度（sessionAuth + USER，F-1046）</li>
 *   <li>{@code GET  /api/user/checkin} 签到状态与本月记录查询（sessionAuth + USER，F-1047）</li>
 * </ul>
 * </p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（注入认证主体 → 调用例 → 裁剪视图），无业务逻辑。签到/统计逻辑在
 * 用例 + 领域值对象/聚合。领域异常由 {@code GrowthExceptionHandler} 统一翻译。</p>
 *
 * <p><b>鉴权（安全声明）</b>：{@link RequireRole}({@link AuthLevel#USER})——均为登录用户 self-scope；
 * 用户 id 由 {@code @CurrentActor} 注入，<b>不</b>从请求参数读 user_id（防伪造他人归属）。</p>
 *
 * <p><b>Turnstile</b>（F-1050）：签到前置人机校验属接口层中间件/过滤器横切关注点（OVERALL-FLOW §3 C4），
 * 不在本控制器方法内重复实现，由全局 Turnstile 校验拦截器在进入本端点前完成。</p>
 */
@RestController
@RequestMapping("/api/user/checkin")
@RequireRole(AuthLevel.USER)
public class CheckinController {

    private final DailyCheckinUseCase dailyCheckinUseCase;
    private final QueryCheckinStatusUseCase queryCheckinStatusUseCase;

    /**
     * @param dailyCheckinUseCase       每日签到用例
     * @param queryCheckinStatusUseCase 签到状态查询用例
     */
    public CheckinController(DailyCheckinUseCase dailyCheckinUseCase,
                            QueryCheckinStatusUseCase queryCheckinStatusUseCase) {
        this.dailyCheckinUseCase = dailyCheckinUseCase;
        this.queryCheckinStatusUseCase = queryCheckinStatusUseCase;
    }

    /**
     * 每日签到（F-1046）。
     *
     * <p>self-scope：签到用户取认证主体 {@code actor.userId()}。未启用 / 今日已签 / Turnstile 失败由
     * 领域异常或前置中间件处理（接口层翻译 400）。</p>
     *
     * @param actor 认证主体（注入，提供本人 user_id）
     * @return 签到结果客户视图（本次发放额度）
     */
    @PostMapping
    public ApiResponse<CheckinResultView> checkin(@CurrentActor AuthenticatedActor actor) {
        DailyCheckinUseCase.CheckinResult result = dailyCheckinUseCase.checkin(actor.userId());
        return ApiResponse.okData(CheckinResultView.from(result));
    }

    /**
     * 签到状态与本月记录查询（F-1047）。
     *
     * <p>self-scope：统计取认证主体本人。{@code month} 缺省本月。返回脱敏记录（不含 id/user_id）。</p>
     *
     * @param actor 认证主体（注入，提供本人 user_id）
     * @param month 月份 {@code YYYY-MM}（可空，默认本月）
     * @return 签到状态与本月脱敏记录客户视图
     */
    @GetMapping
    public ApiResponse<CheckinStatusView> status(
            @CurrentActor AuthenticatedActor actor,
            @RequestParam(value = "month", required = false) String month) {
        CheckinStats stats = queryCheckinStatusUseCase.query(actor.userId(), month);
        return ApiResponse.okData(CheckinStatusView.from(stats));
    }
}
