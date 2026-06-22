package com.nexa.model.interfaces.api;

import com.nexa.model.application.ManagePlatformModelMappingUseCase;
import com.nexa.model.domain.model.PlatformModelMapping;
import com.nexa.model.domain.vo.Pagination;
import com.nexa.shared.web.ApiResponse;
import com.nexa.model.interfaces.api.dto.*;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 超管底仓映射管理控制器（AdminAuth/RootAuth 端点，接口层，F-6002）。
 *
 * <p>承载底仓映射全部端点（对齐 openapi /api/platform_model_mappings*）：
 * <ul>
 *   <li>{@code GET    /api/platform_model_mappings}        列表分页（F-6002）</li>
 *   <li>{@code POST   /api/platform_model_mappings}        创建 A→B 映射（F-6002）</li>
 *   <li>{@code PUT    /api/platform_model_mappings}        更新 A→B 映射（F-6002）</li>
 *   <li>{@code DELETE /api/platform_model_mappings/{id}}   删除映射（F-6002）</li>
 * </ul>
 * </p>
 *
 * <p><b>B 不可见三道闸——接口层闸</b>：本控制器响应 {@link PlatformModelMappingAdminView}（含 B），
 * 仅 admin/root 有权；无任何客户路由触达本控制器。</p>
 */
@RestController
@RequestMapping("/api/platform_model_mappings")
@RequireRole(AuthLevel.ADMIN)
public class PlatformModelMappingController {

    private final ManagePlatformModelMappingUseCase useCase;

    /** @param useCase 底仓映射 CRUD 用例 */
    public PlatformModelMappingController(ManagePlatformModelMappingUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * 底仓映射列表分页（F-6002，{@code GET /api/platform_model_mappings}）。
     *
     * @param page     query 页号（可空→1）
     * @param pageSize query 每页条数（可空→10）
     * @return 成功信封，data = { items[], total }（AdminView，含 B）
     */
    @GetMapping
    public ApiResponse<PlatformModelMappingListView> list(
            @RequestParam(name = "p", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        Pagination pagination = Pagination.of(page, pageSize);
        List<PlatformModelMappingAdminView> items = useCase.list(pagination).stream()
                .map(PlatformModelMappingAdminView::from).toList();
        return ApiResponse.okData(new PlatformModelMappingListView(items, useCase.count()));
    }

    /**
     * 创建 A→B 映射（F-6002，{@code POST /api/platform_model_mappings}）。
     *
     * @param request 创建请求（public_name / upstream_name 必填）
     * @return 成功信封，data = 创建后映射（AdminView，含 B）
     */
    @PostMapping
    public ApiResponse<PlatformModelMappingAdminView> create(@RequestBody PlatformModelMappingCreateRequest request) {
        PlatformModelMapping created = useCase.create(request.publicName(), request.upstreamName(),
                request.enabled(), request.remark());
        return ApiResponse.okData(PlatformModelMappingAdminView.from(created));
    }

    /**
     * 更新 A→B 映射（F-6002，{@code PUT /api/platform_model_mappings}）。
     *
     * @param request 更新请求（id 必填；A 不可改）
     * @return 成功信封，data = 更新后映射（AdminView，含 B）
     */
    @PutMapping
    public ApiResponse<PlatformModelMappingAdminView> update(@RequestBody PlatformModelMappingUpdateRequest request) {
        PlatformModelMapping updated = useCase.update(request.id(), request.upstreamName(),
                request.enabled(), request.remark());
        return ApiResponse.okData(PlatformModelMappingAdminView.from(updated));
    }

    /**
     * 删除 A→B 映射（F-6002，{@code DELETE /api/platform_model_mappings/{id}}）。
     *
     * @param id path 映射 id
     * @return 成功信封（无 data）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") long id) {
        useCase.delete(id);
        return ApiResponse.ok("mapping deleted");
    }
}
