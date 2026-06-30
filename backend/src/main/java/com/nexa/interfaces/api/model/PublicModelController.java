package com.nexa.interfaces.api.model;

import com.nexa.application.model.ManagePublicModelUseCase;
import com.nexa.domain.model.model.PublicModel;
import com.nexa.domain.model.vo.Pagination;
import com.nexa.interfaces.web.ApiResponse;
import com.nexa.interfaces.api.model.dto.*;
import com.nexa.domain.security.rbac.AuthLevel;
import com.nexa.interfaces.security.annotation.RequireRole;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对外模型商品目录管理控制器（AdminAuth 端点，接口层，F-6001/F-6004）。
 *
 * <p>承载对外模型全部端点（对齐 openapi /api/public_models*）：
 * <ul>
 *   <li>{@code GET    /api/public_models}        列表分页（F-6001，可选 enabled 过滤）</li>
 *   <li>{@code POST   /api/public_models}        创建对外模型（F-6001，品质拆分独立定价）</li>
 *   <li>{@code PUT    /api/public_models}        更新对外模型（F-6001/F-6004，含上下架）</li>
 *   <li>{@code DELETE /api/public_models/{id}}   删除对外模型（F-6001 软删，移出对外全集）</li>
 * </ul>
 * </p>
 *
 * <p><b>F-6004 模型权限全开</b>：无独立端点；上架即全员可用（enabled=true），通过 PUT 切换。</p>
 *
 * <p>DDD：接口层只做协议翻译；业务规则在领域聚合 + 用例。异常由 {@code ModelExceptionHandler} 统一翻译。
 * 类级 {@link RequireRole}({@link AuthLevel#ADMIN})。</p>
 */
@RestController
@RequestMapping("/api/public_models")
@RequireRole(AuthLevel.ADMIN)
public class PublicModelController {

    private final ManagePublicModelUseCase useCase;

    /** @param useCase 对外模型 CRUD 用例 */
    public PublicModelController(ManagePublicModelUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * 对外模型列表分页（F-6001，{@code GET /api/public_models}）。
     *
     * @param page     query 页号（可空→1）
     * @param pageSize query 每页条数（可空→10）
     * @param enabled  query 过滤是否上架（可空→不过滤，返回全部）
     * @return 成功信封，data = { items[], total }（AdminView）
     */
    @GetMapping
    public ApiResponse<PublicModelListVO> list(
            @RequestParam(name = "p", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "enabled", required = false) Boolean enabled) {
        boolean filterEnabled = enabled != null && enabled;
        Pagination pagination = Pagination.of(page, pageSize);
        List<PublicModelAdminVO> items = useCase.list(pagination, filterEnabled).stream()
                .map(PublicModelAdminVO::from).toList();
        return ApiResponse.okData(new PublicModelListVO(items, useCase.count(filterEnabled)));
    }

    /**
     * 创建对外模型（F-6001，{@code POST /api/public_models}）。
     *
     * @param request 创建请求（public_name 必填）
     * @return 成功信封，data = 创建后对外模型（AdminView）
     */
    @PostMapping
    public ApiResponse<PublicModelAdminVO> create(@RequestBody PublicModelCreateRequest request) {
        PublicModel created = useCase.create(request.publicName(),
                request.basePriceRatio(), request.usePrice(), request.basePrice(), request.enabled(),
                request.displayName(), request.sortOrder(), request.description());
        return ApiResponse.okData(PublicModelAdminVO.from(created));
    }

    /**
     * 更新对外模型（F-6001/F-6004，{@code PUT /api/public_models}）。
     *
     * @param request 更新请求（id 必填；A 不可改）
     * @return 成功信封，data = 更新后对外模型（AdminView）
     */
    @PutMapping
    public ApiResponse<PublicModelAdminVO> update(@RequestBody PublicModelUpdateRequest request) {
        PublicModel updated = useCase.update(request.id(),
                request.basePriceRatio(), request.usePrice(), request.basePrice(), request.enabled(),
                request.displayName(), request.sortOrder(), request.description());
        return ApiResponse.okData(PublicModelAdminVO.from(updated));
    }

    /**
     * 删除对外模型（F-6001，{@code DELETE /api/public_models/{id}}）。
     *
     * @param id path 对外模型 id
     * @return 成功信封（无 data）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") long id) {
        useCase.delete(id);
        return ApiResponse.ok("public model deleted");
    }
}
