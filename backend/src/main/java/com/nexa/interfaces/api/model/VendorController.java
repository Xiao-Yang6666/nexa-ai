package com.nexa.interfaces.api.model;

import com.nexa.application.model.ManageVendorUseCase;
import com.nexa.domain.model.model.Vendor;
import com.nexa.domain.model.vo.Pagination;
import com.nexa.interfaces.web.ApiResponse;
import com.nexa.interfaces.api.model.dto.VendorAdminVO;
import com.nexa.interfaces.api.model.dto.VendorListVO;
import com.nexa.interfaces.api.model.dto.VendorWriteRequest;
import com.nexa.domain.security.rbac.AuthLevel;
import com.nexa.interfaces.security.annotation.RequireRole;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 供应商元数据管理控制器（AdminAuth 端点，接口层，F-3018）。
 *
 * <p>承载供应商元数据全部端点（对齐 openapi /api/vendors*）：
 * <ul>
 *   <li>{@code GET    /api/vendors}        供应商列表分页（F-3018）</li>
 *   <li>{@code POST   /api/vendors}        创建供应商（F-3018，名称查重）</li>
 *   <li>{@code PUT    /api/vendors}        更新供应商（F-3018）</li>
 *   <li>{@code GET    /api/vendors/search} 供应商搜索（F-3018）</li>
 *   <li>{@code DELETE /api/vendors/{id}}   删除供应商（F-3018）</li>
 * </ul>
 * </p>
 *
 * <p>DDD 铁律：接口层只做协议翻译，无业务逻辑。名称非空/查重在领域聚合 + 用例。异常由
 * {@code ModelExceptionHandler} 统一翻译。类级 {@link RequireRole}({@link AuthLevel#ADMIN})。</p>
 */
@RestController
@RequestMapping("/api/vendors")
@RequireRole(AuthLevel.ADMIN)
public class VendorController {

    private final ManageVendorUseCase vendorUseCase;

    /** @param vendorUseCase 供应商 CRUD 用例 */
    public VendorController(ManageVendorUseCase vendorUseCase) {
        this.vendorUseCase = vendorUseCase;
    }

    /**
     * 供应商列表分页（F-3018，{@code GET /api/vendors}）。
     *
     * @param page     query 页号（可空→1）
     * @param pageSize query 每页条数（可空→10）
     * @return 成功信封，data = { items[], total }（AdminView）
     */
    @GetMapping
    public ApiResponse<VendorListVO> list(
            @RequestParam(name = "p", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        Pagination pagination = Pagination.of(page, pageSize);
        List<VendorAdminVO> items = vendorUseCase.list(pagination).stream()
                .map(VendorAdminVO::from).toList();
        return ApiResponse.okData(new VendorListVO(items, vendorUseCase.count()));
    }

    /**
     * 创建供应商（F-3018，{@code POST /api/vendors}）。
     *
     * @param request 写请求（name 必填）
     * @return 成功信封，data = 创建后供应商（AdminView）
     */
    @PostMapping
    public ApiResponse<VendorAdminVO> create(@RequestBody VendorWriteRequest request) {
        Vendor created = vendorUseCase.create(request.name(), request.icon(), request.status());
        return ApiResponse.okData(VendorAdminVO.from(created));
    }

    /**
     * 更新供应商（F-3018，{@code PUT /api/vendors}）。
     *
     * @param request 写请求（id/name 必填）
     * @return 成功信封，data = 更新后供应商（AdminView）
     */
    @PutMapping
    public ApiResponse<VendorAdminVO> update(@RequestBody VendorWriteRequest request) {
        Vendor updated = vendorUseCase.update(request.id(), request.name(), request.icon(), request.status());
        return ApiResponse.okData(VendorAdminVO.from(updated));
    }

    /**
     * 供应商搜索（F-3018，{@code GET /api/vendors/search}）。
     *
     * @param keyword query 关键词（可空白→全量）
     * @return 成功信封，data = { items[], total }（AdminView）
     */
    @GetMapping("/search")
    public ApiResponse<VendorListVO> search(
            @RequestParam(name = "keyword", required = false) String keyword) {
        List<VendorAdminVO> items = vendorUseCase.search(keyword).stream()
                .map(VendorAdminVO::from).toList();
        return ApiResponse.okData(new VendorListVO(items, items.size()));
    }

    /**
     * 删除供应商（F-3018，{@code DELETE /api/vendors/{id}}）。
     *
     * @param id path 供应商 id
     * @return 成功信封（无 data）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") long id) {
        vendorUseCase.delete(id);
        return ApiResponse.ok("vendor deleted");
    }
}
