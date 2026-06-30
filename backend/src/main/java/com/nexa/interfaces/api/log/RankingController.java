package com.nexa.interfaces.api.log;

import com.nexa.application.log.QueryRankingUseCase;
import com.nexa.interfaces.web.ApiResponse;
import com.nexa.interfaces.api.log.dto.RankingPublicVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用量排行榜控制器（公开/可选 UserAuth 端点，接口层，F-4010 GET /api/rankings）。
 *
 * <p>对齐 openapi {@code GET /api/rankings}（security: sessionAuth[] 或 []，即匿名亦可访问，
 * 真正可见性由前端导航模块开关 HeaderNavModuleAuth rankings 控制）。本控制器<b>不</b>标
 * {@code @RequireRole}——公开端点，匿名可访问；不注入 {@code @CurrentActor}（排行不区分用户）。</p>
 *
 * <p><b>可见性铁律</b>：输出一律 {@link RankingPublicVO}（只对外公开名 A + 聚合用量，绝不含
 * 成本/利润/上游模型 B/供应商）——排行对外公开，是「客户看不到 B」铁律最严格的场景。
 * period 非法由用例（{@code Period.parse}）抛 {@code InvalidLogQueryException}「invalid period」→ 400。</p>
 */
@RestController
@RequestMapping("/api/rankings")
public class RankingController {

    private final QueryRankingUseCase queryRankingUseCase;

    /** @param queryRankingUseCase 排行查询用例（F-4010） */
    public RankingController(QueryRankingUseCase queryRankingUseCase) {
        this.queryRankingUseCase = queryRankingUseCase;
    }

    /**
     * 用量排行榜（F-4010，{@code GET /api/rankings}，period=week|month 缺省 week）。
     *
     * @param period 统计周期（null/空白→week；非 week/month→400 invalid period）
     * @return 成功信封，data = RankingPublicVO 数组（rank 升序=用量降序）
     */
    @GetMapping
    public ApiResponse<List<RankingPublicVO>> rankings(
            @RequestParam(name = "period", required = false) String period) {

        List<RankingPublicVO> data = queryRankingUseCase.query(period)
                .stream().map(RankingPublicVO::from).toList();
        return ApiResponse.okData(data);
    }
}
