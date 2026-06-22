package com.nexa.publicsite.interfaces.api;

import com.nexa.publicsite.application.QuerySiteStatusUseCase;
import com.nexa.shared.web.ApiResponse;
import com.nexa.publicsite.interfaces.api.dto.StatusAggregateView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 营销首页公开状态聚合控制器（接口层，F-4039 GET /api/status，公开端点）。
 *
 * <p>对齐 openapi {@code GET /api/status}（security: []，匿名可访问）。本控制器<b>不</b>标
 * {@code @RequireRole}、不注入 {@code @CurrentActor}——纯公开端点，前端首页据此渲染系统名/登录入口等。</p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（backend-engineer §2.1）。状态装配与「零敏感字段」由
 * domain/application/infra 负责，本类只投影 {@link StatusAggregateView}（敏感配置在此根本读不到）。</p>
 */
@RestController
@RequestMapping("/api/status")
public class PublicStatusController {

    private final QuerySiteStatusUseCase querySiteStatusUseCase;

    /**
     * @param querySiteStatusUseCase 公开状态聚合查询用例（F-4039）
     */
    public PublicStatusController(QuerySiteStatusUseCase querySiteStatusUseCase) {
        this.querySiteStatusUseCase = querySiteStatusUseCase;
    }

    /**
     * 读取营销首页公开状态聚合（F-4039）。
     *
     * @return 成功信封，data = 公开状态视图（无任何敏感字段）
     */
    @GetMapping
    public ApiResponse<StatusAggregateView> status() {
        return ApiResponse.okData(StatusAggregateView.from(querySiteStatusUseCase.query()));
    }
}
