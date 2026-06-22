package com.nexa.model.interfaces.api;

import com.nexa.model.application.CreateModelMetaUseCase;
import com.nexa.model.application.DeleteModelMetaUseCase;
import com.nexa.model.application.DetectMissingModelsUseCase;
import com.nexa.model.application.ListModelMetasUseCase;
import com.nexa.model.application.SearchModelMetasUseCase;
import com.nexa.model.application.SyncUpstreamModelsUseCase;
import com.nexa.model.application.UpdateModelMetaUseCase;
import com.nexa.model.domain.vo.Pagination;
import com.nexa.shared.web.ApiResponse;
import com.nexa.model.interfaces.api.dto.ModelMetaAdminView;
import com.nexa.model.interfaces.api.dto.ModelMetaCreateRequest;
import com.nexa.model.interfaces.api.dto.ModelMetaListView;
import com.nexa.model.interfaces.api.dto.ModelMetaUpdateRequest;
import com.nexa.model.interfaces.api.dto.ModelSyncDiffView;
import com.nexa.model.interfaces.api.dto.ModelSyncExecuteRequest;
import com.nexa.model.interfaces.api.dto.ModelSyncPreviewRequest;
import com.nexa.model.interfaces.api.dto.ModelSyncResultView;
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
 * 模型元数据管理控制器（AdminAuth 端点，接口层，F-3013~F-3021）。
 *
 * <p>承载模型元数据全部管理端点（对齐 openapi /api/models*）：
 * <ul>
 *   <li>{@code GET    /api/models}               模型列表分页 + 供应商计数（F-3013）</li>
 *   <li>{@code POST   /api/models}               创建模型（F-3015，名称查重 + RefreshPricing）</li>
 *   <li>{@code PUT    /api/models}               更新模型（F-3016，含 status_only）</li>
 *   <li>{@code GET    /api/models/search}        模型搜索（F-3014，关键词 + 供应商过滤）</li>
 *   <li>{@code DELETE /api/models/{id}}          删除模型（F-3017，RefreshPricing）</li>
 *   <li>{@code POST   /api/models/sync/preview}  上游同步预览（F-3020，只读差异）</li>
 *   <li>{@code POST   /api/models/sync}          上游同步执行（F-3019）</li>
 *   <li>{@code GET    /api/models/missing}       缺失模型检测（F-3021）</li>
 * </ul>
 * </p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（绑定入参 → 调用用例 → 裁剪视图），无业务逻辑。
 * 领域规则（名称非空/查重、status_only 字段保留、同步比对）在领域聚合/领域服务/用例。
 * 领域/上游异常由 {@code ModelExceptionHandler} 统一翻译（400/404/502）。</p>
 *
 * <p><b>鉴权（安全声明）</b>：契约要求全 {@code /api/models*}（管理面）= AdminAuth。类级
 * {@link RequireRole}({@link AuthLevel#ADMIN})，由 {@code RequireRoleInterceptor} 统一拦截判定，
 * 未认证→401、越权→403（不裸奔）。F-3025 用户可见模型为 sessionAuth，独立放
 * {@code UserModelController}（路径 /api/user/self/models）。</p>
 *
 * <p><b>客户视图铁律</b>：出参用 {@link ModelMetaAdminView}（仅对外模型名 A + 展示元数据，无上游
 * 模型 B / 成本 / 供应商凭证——产品三道闸）。</p>
 */
@RestController
@RequestMapping("/api/models")
@RequireRole(AuthLevel.ADMIN)
public class ModelController {

    private final ListModelMetasUseCase listUseCase;
    private final SearchModelMetasUseCase searchUseCase;
    private final CreateModelMetaUseCase createUseCase;
    private final UpdateModelMetaUseCase updateUseCase;
    private final DeleteModelMetaUseCase deleteUseCase;
    private final SyncUpstreamModelsUseCase syncUseCase;
    private final DetectMissingModelsUseCase missingUseCase;

    /**
     * @param listUseCase    列表用例（F-3013）
     * @param searchUseCase  搜索用例（F-3014）
     * @param createUseCase  创建用例（F-3015）
     * @param updateUseCase  更新用例（F-3016）
     * @param deleteUseCase  删除用例（F-3017）
     * @param syncUseCase    上游同步用例（F-3019/F-3020）
     * @param missingUseCase 缺失检测用例（F-3021）
     */
    public ModelController(ListModelMetasUseCase listUseCase,
                           SearchModelMetasUseCase searchUseCase,
                           CreateModelMetaUseCase createUseCase,
                           UpdateModelMetaUseCase updateUseCase,
                           DeleteModelMetaUseCase deleteUseCase,
                           SyncUpstreamModelsUseCase syncUseCase,
                           DetectMissingModelsUseCase missingUseCase) {
        this.listUseCase = listUseCase;
        this.searchUseCase = searchUseCase;
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
        this.syncUseCase = syncUseCase;
        this.missingUseCase = missingUseCase;
    }

    /**
     * 模型列表分页（F-3013，{@code GET /api/models}）。
     *
     * @param page     query 页号（可空→1）
     * @param pageSize query 每页条数（可空→10）
     * @return 成功信封，data = { items[], total, vendor_counts }（AdminView）
     */
    @GetMapping
    public ApiResponse<ModelMetaListView> list(
            @RequestParam(name = "p", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        Pagination pagination = Pagination.of(page, pageSize);
        return ApiResponse.okData(ModelMetaListView.from(listUseCase.list(pagination)));
    }

    /**
     * 创建模型（F-3015，{@code POST /api/models}）。
     *
     * @param request 创建请求（model_name 必填）
     * @return 成功信封，data = 创建后模型（AdminView）
     */
    @PostMapping
    public ApiResponse<ModelMetaAdminView> create(@RequestBody ModelMetaCreateRequest request) {
        return ApiResponse.okData(ModelMetaAdminView.from(createUseCase.create(request.toCommand())));
    }

    /**
     * 更新模型（F-3016，{@code PUT /api/models}，含 status_only）。
     *
     * @param request 更新请求（id 必填）
     * @return 成功信封，data = 更新后模型（AdminView）
     */
    @PutMapping
    public ApiResponse<ModelMetaAdminView> update(@RequestBody ModelMetaUpdateRequest request) {
        return ApiResponse.okData(ModelMetaAdminView.from(updateUseCase.update(request.toCommand())));
    }

    /**
     * 模型搜索（F-3014，{@code GET /api/models/search}，关键词 + 供应商过滤）。
     *
     * @param keyword  query 关键词（可空白→不过滤）
     * @param vendor   query 供应商过滤（可空）
     * @param page     query 页号（可空→1）
     * @param pageSize query 每页条数（可空→10）
     * @return 成功信封，data = { items[], total }（AdminView）
     */
    @GetMapping("/search")
    public ApiResponse<ModelMetaListView> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "vendor", required = false) Long vendor,
            @RequestParam(name = "p", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        Pagination pagination = Pagination.of(page, pageSize);
        return ApiResponse.okData(ModelMetaListView.from(searchUseCase.search(keyword, vendor, pagination)));
    }

    /**
     * 删除模型（F-3017，{@code DELETE /api/models/{id}}）。
     *
     * @param id path 模型 id
     * @return 成功信封（无 data）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") long id) {
        deleteUseCase.delete(id);
        return ApiResponse.ok("model deleted");
    }

    /**
     * 上游模型同步预览（F-3020，{@code POST /api/models/sync/preview}，只读差异不写库）。
     *
     * @param request 预览请求（locale 可空）
     * @return 成功信封，data = 同步差异（AdminView）
     */
    @PostMapping("/sync/preview")
    public ApiResponse<ModelSyncDiffView> syncPreview(
            @RequestBody(required = false) ModelSyncPreviewRequest request) {
        String locale = request == null ? null : request.locale();
        return ApiResponse.okData(ModelSyncDiffView.from(syncUseCase.preview(locale)));
    }

    /**
     * 上游模型同步执行（F-3019，{@code POST /api/models/sync}）。
     *
     * @param request 执行请求（locale/overwrite/models 可空）
     * @return 成功信封，data = 同步结果计数（AdminView）
     */
    @PostMapping("/sync")
    public ApiResponse<ModelSyncResultView> syncExecute(
            @RequestBody(required = false) ModelSyncExecuteRequest request) {
        String locale = request == null ? null : request.locale();
        boolean overwrite = request != null && Boolean.TRUE.equals(request.overwrite());
        List<String> models = request == null ? null : request.models();
        return ApiResponse.okData(ModelSyncResultView.from(syncUseCase.execute(locale, overwrite, models)));
    }

    /**
     * 缺失模型检测（F-3021，{@code GET /api/models/missing}）。
     *
     * @return 成功信封，data = 缺失模型名数组（AdminView）
     */
    @GetMapping("/missing")
    public ApiResponse<List<String>> missing() {
        return ApiResponse.okData(missingUseCase.detect());
    }
}
