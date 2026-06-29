package com.nexa.interfaces.model.api;

import com.nexa.application.model.QueryPublicPricingUseCase;
import com.nexa.shared.web.ApiResponse;
import com.nexa.interfaces.model.api.dto.PricingPublicVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开模型价格页控制器（接口层，F-2048 / ML-4，<b>公开端点</b>）。
 *
 * <p>对齐 openapi {@code GET /api/pricing}（{@code security: []}，匿名可访问）。本控制器<b>不</b>标
 * {@code @RequireRole}、<b>不</b>注入 {@code @CurrentActor}——纯公开端点，未登录即可拉取公开价格页
 * （沿用同项目 {@code PublicStatusController} 的公开端点写法）。SecurityConfig 已将 {@code /api/pricing}
 * 列入 permitAll 精确白名单；本 handler 存在后 MvcRequestMatcher 方能解析该映射、放行才真正生效。</p>
 *
 * <p>DDD：接口层只做协议翻译（backend-engineer §2.1）。定价装配与「零泄露」由 application
 * ({@link QueryPublicPricingUseCase}) + DTO ({@link PricingPublicVO}) 负责——成本/利润/上游模型 B/
 * 渠道/供应商等内部字段在本投影链路上根本读不到（产品铁律，COMPAT §4）。</p>
 */
@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private final QueryPublicPricingUseCase queryPublicPricingUseCase;

    /** @param queryPublicPricingUseCase 公开价格页查询用例 */
    public PricingController(QueryPublicPricingUseCase queryPublicPricingUseCase) {
        this.queryPublicPricingUseCase = queryPublicPricingUseCase;
    }

    /**
     * 公开模型价格页（F-2048，{@code GET /api/pricing}）。
     *
     * @param locale query 展示语言（可空；ML-4 元信息入参，预留）
     * @return 成功信封，data = 公开价格页视图（PublicView 零泄露）
     */
    @GetMapping
    public ApiResponse<PricingPublicVO> pricing(
            @RequestParam(name = "locale", required = false) String locale) {
        return ApiResponse.okData(queryPublicPricingUseCase.query(locale));
    }
}
