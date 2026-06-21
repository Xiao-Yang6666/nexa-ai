package com.nexa.prefill.interfaces.api;

import com.nexa.prefill.application.CreatePrefillGroupUseCase;
import com.nexa.prefill.application.DeletePrefillGroupUseCase;
import com.nexa.prefill.application.ListPrefillGroupsUseCase;
import com.nexa.prefill.application.UpdatePrefillGroupUseCase;
import com.nexa.prefill.domain.model.PrefillGroup;
import com.nexa.prefill.interfaces.api.dto.ApiResponse;
import com.nexa.prefill.interfaces.api.dto.PrefillGroupAdminView;
import com.nexa.prefill.interfaces.api.dto.PrefillGroupCreateRequest;
import com.nexa.prefill.interfaces.api.dto.PrefillGroupUpdateRequest;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
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
 * 预填分组管理控制器（AdminAuth 端点，接口层，PRD 模块十五 §14，F-2012~F-2015）。
 *
 * <p>承载预填分组端点（对齐 openapi {@code /api/prefill_group*} 的 adminAuth operation）：
 * <ul>
 *   <li>{@code GET    /api/prefill_group}       列表/下拉填充（可选 type，F-2014）</li>
 *   <li>{@code POST   /api/prefill_group}       创建（名称冲突校验，F-2012）</li>
 *   <li>{@code PUT    /api/prefill_group}       更新（名称冲突校验，F-2013）</li>
 *   <li>{@code DELETE /api/prefill_group/{id}}  软删除（F-2015）</li>
 * </ul></p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（HTTP DTO ⇄ 用例命令/聚合），不含业务逻辑——名称冲突/类型校验/
 * 软删除规则全在 domain/application。鉴权：类级 {@link RequireRole}{@code (ADMIN)} 要求 Role≥admin
 * （openapi 全为 adminAuth）；领域异常由 {@code PrefillExceptionHandler} 翻译为 HTTP 状态码。
 * 出参统一 AdminView（预填分组非客户敏感资源，无脱敏裁剪需求）。</p>
 */
@RestController
@RequestMapping("/api/prefill_group")
@RequireRole(AuthLevel.ADMIN)
public class PrefillGroupController {

    private final ListPrefillGroupsUseCase listUseCase;
    private final CreatePrefillGroupUseCase createUseCase;
    private final UpdatePrefillGroupUseCase updateUseCase;
    private final DeletePrefillGroupUseCase deleteUseCase;

    /**
     * @param listUseCase   列表用例
     * @param createUseCase 创建用例
     * @param updateUseCase 更新用例
     * @param deleteUseCase 软删除用例
     */
    public PrefillGroupController(ListPrefillGroupsUseCase listUseCase,
                                  CreatePrefillGroupUseCase createUseCase,
                                  UpdatePrefillGroupUseCase updateUseCase,
                                  DeletePrefillGroupUseCase deleteUseCase) {
        this.listUseCase = listUseCase;
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
    }

    /**
     * 预填分组列表（F-2014，对齐 openapi {@code GET /api/prefill_group}）。
     *
     * @param type 可选类型过滤（model/tag/endpoint；缺省返回全部，非法枚举 → 400）
     * @return 成功信封，data 为管理视图列表
     */
    @GetMapping
    public ApiResponse<List<PrefillGroupAdminView>> list(
            @RequestParam(name = "type", required = false) String type) {

        List<PrefillGroupAdminView> views = listUseCase.list(type).stream()
                .map(PrefillGroupAdminView::from)
                .toList();
        return ApiResponse.okData(views);
    }

    /**
     * 创建预填分组（F-2012，对齐 openapi {@code POST /api/prefill_group}）。
     *
     * @param request 创建请求（名称/类型/条目/描述）
     * @return 成功信封，data 为创建后的管理视图
     */
    @PostMapping
    public ApiResponse<PrefillGroupAdminView> create(@RequestBody PrefillGroupCreateRequest request) {
        PrefillGroup created = createUseCase.create(request.toCommand());
        return ApiResponse.okData(PrefillGroupAdminView.from(created));
    }

    /**
     * 更新预填分组（F-2013，对齐 openapi {@code PUT /api/prefill_group}）。
     *
     * @param request 更新请求（id 必填；name/items 可空）
     * @return 成功信封，data 为更新后的管理视图
     */
    @PutMapping
    public ApiResponse<PrefillGroupAdminView> update(@RequestBody PrefillGroupUpdateRequest request) {
        PrefillGroup updated = updateUseCase.update(request.toCommand());
        return ApiResponse.okData(PrefillGroupAdminView.from(updated));
    }

    /**
     * 软删除预填分组（F-2015，对齐 openapi {@code DELETE /api/prefill_group/{id}}）。
     *
     * @param id 分组主键（path）
     * @return 成功信封（无 data）；id 不存在由异常处理器翻译为 404
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") long id) {
        deleteUseCase.delete(id);
        return ApiResponse.ok("prefill group deleted");
    }
}
