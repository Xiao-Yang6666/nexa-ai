package com.nexa.billing.interfaces.api;

import com.nexa.billing.application.GenerateRedemptionsCommand;
import com.nexa.billing.application.GenerateRedemptionsUseCase;
import com.nexa.billing.application.ListRedemptionsUseCase;
import com.nexa.billing.domain.model.Redemption;
import com.nexa.billing.domain.repository.RedemptionRepository;
import com.nexa.shared.web.ApiResponse;
import com.nexa.shared.web.PageView;
import com.nexa.billing.interfaces.api.dto.RedemptionAdminView;
import com.nexa.billing.interfaces.api.dto.RedemptionCreateRequest;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.domain.rbac.AuthenticatedActor;
import com.nexa.shared.security.interfaces.annotation.CurrentActor;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 兑换码管理控制器（AdminAuth 端点，接口层，prd-billing BL-4，F-2045）。
 *
 * <p>承载兑换码管理端点（对齐 openapi {@code /api/redemption/} 的 adminAuth operation）：
 * <ul>
 *   <li>{@code GET  /api/redemption/}  兑换码分页列表 → {@link ListRedemptionsUseCase}</li>
 *   <li>{@code POST /api/redemption/}  生成兑换码（单个/批量）→ {@link GenerateRedemptionsUseCase}</li>
 * </ul></p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（HTTP DTO ⇄ 用例命令/结果），不含业务逻辑——一次性/过期守卫、
 * 生成规则全在 domain/application 内。鉴权：类级 {@link RequireRole}{@code (ADMIN)} 要求 Role≥admin；
 * 领域异常由 {@code BillingExceptionHandler} 翻译为 HTTP 状态码。</p>
 */
@RestController
@RequestMapping("/api/redemption")
@RequireRole(AuthLevel.ADMIN)
public class RedemptionController {

    private final ListRedemptionsUseCase listRedemptionsUseCase;
    private final GenerateRedemptionsUseCase generateRedemptionsUseCase;

    /**
     * @param listRedemptionsUseCase     兑换码列表用例
     * @param generateRedemptionsUseCase 兑换码生成用例
     */
    public RedemptionController(ListRedemptionsUseCase listRedemptionsUseCase,
                                GenerateRedemptionsUseCase generateRedemptionsUseCase) {
        this.listRedemptionsUseCase = listRedemptionsUseCase;
        this.generateRedemptionsUseCase = generateRedemptionsUseCase;
    }

    /**
     * 兑换码分页列表（F-2045，对齐 openapi {@code GET /api/redemption/}）。
     *
     * @param page     页码（query {@code p}，缺省 1）
     * @param pageSize 每页条数（query {@code page_size}，缺省 20）
     * @return 成功信封，data 为分页的管理视图列表
     */
    // 同时映射 "" 与 "/"：契约路径 /api/redemption/（带尾斜杠），但调用方常以 /api/redemption（无尾斜杠）发起。
    // Spring Boot 3 默认关闭尾斜杠匹配，单映射 "/" 时无尾斜杠请求无 handler → 403（落 anyRequest）。两形态都登记，
    // 鉴权不削弱：类级 @RequireRole(ADMIN) + 方法级拦截器对两路径同样生效（普通用户仍 403）。
    @GetMapping({"", "/"})
    public ApiResponse<PageView<RedemptionAdminView>> list(
            @RequestParam(name = "p", required = false, defaultValue = "1") int page,
            @RequestParam(name = "page_size", required = false, defaultValue = "20") int pageSize) {

        RedemptionRepository.Page<Redemption> result = listRedemptionsUseCase.list(page, pageSize);
        List<RedemptionAdminView> items = result.items().stream()
                .map(RedemptionAdminView::from)
                .toList();
        return ApiResponse.okData(
                new PageView<>(items, result.total(), result.page(), result.pageSize()));
    }

    /**
     * 生成兑换码（单个/批量，F-2045，对齐 openapi {@code POST /api/redemption/}）。
     *
     * <p>返回生成的兑换码明文集合（openapi 200 = string[]）。创建者取自认证主体（非伪造）。</p>
     *
     * @param request 生成请求
     * @param admin   认证主体（AdminAuth 注入，提供真实创建者 id）
     * @return 成功信封，data 为明文兑换码列表
     */
    @PostMapping("/")
    public ApiResponse<List<String>> generate(
            @RequestBody RedemptionCreateRequest request,
            @CurrentActor AuthenticatedActor admin) {

        GenerateRedemptionsCommand command = new GenerateRedemptionsCommand(
                request.name(), request.quota(), request.count(), request.expiredTime());
        List<String> keys = generateRedemptionsUseCase.generate((int) admin.userId(), command);
        return ApiResponse.okData(keys);
    }
}
