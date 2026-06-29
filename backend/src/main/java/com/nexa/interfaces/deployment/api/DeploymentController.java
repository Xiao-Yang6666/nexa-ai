package com.nexa.interfaces.deployment.api;

import com.nexa.application.deployment.CreateDeploymentUseCase;
import com.nexa.application.deployment.DeleteDeploymentUseCase;
import com.nexa.application.deployment.ExtendDeploymentUseCase;
import com.nexa.application.deployment.GetDeploymentDetailUseCase;
import com.nexa.application.deployment.ListDeploymentsUseCase;
import com.nexa.application.deployment.RenameDeploymentUseCase;
import com.nexa.application.deployment.SearchDeploymentsUseCase;
import com.nexa.application.deployment.UpdateDeploymentUseCase;
import com.nexa.domain.deployment.vo.DeploymentId;
import com.nexa.domain.deployment.vo.DeploymentName;
import com.nexa.domain.deployment.vo.Pagination;
import com.nexa.shared.web.ApiResponse;
import com.nexa.interfaces.deployment.api.dto.DeploymentListView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 部署 CRUD 与运维控制器（AdminAuth 端点，接口层，F-3041~F-3048）。
 *
 * <p>承载部署管理「部署 CRUD 与运维」端点（对齐 API-ENDPOINTS §10.2）：
 * <ul>
 *   <li>{@code GET    /api/deployments/}            部署列表 + 状态计数（F-3041）</li>
 *   <li>{@code GET    /api/deployments/search}      部署搜索（状态过滤 + 名称关键词，F-3042）</li>
 *   <li>{@code GET    /api/deployments/{id}}        部署详情（F-3043）</li>
 *   <li>{@code POST   /api/deployments/}            创建部署（F-3044）</li>
 *   <li>{@code PUT    /api/deployments/{id}}        部署更新（F-3045）</li>
 *   <li>{@code PUT    /api/deployments/{id}/name}   部署重命名（F-3046）</li>
 *   <li>{@code POST   /api/deployments/{id}/extend} 部署续期（F-3047）</li>
 *   <li>{@code DELETE /api/deployments/{id}}        删除/终止部署（F-3048）</li>
 * </ul>
 * </p>
 *
 * <p>DDD 铁律：接口层只做协议翻译。id/name 非空校验在值对象构造点（{@link DeploymentId}/{@link DeploymentName}），
 * 分页归一在 {@link Pagination}，status_counts/关键词过滤在领域聚合，上游交互/名称可用性预检在 infra
 * （backend-engineer §2.1）。参数错误→400、名称不可用→409、上游集成错误→502，由
 * {@code DeploymentExceptionHandler} 统一翻译。请求体（创建/更新/续期）透传上游开放结构，非 JSON 对象由
 * 反序列化阶段拒绝（→400）。</p>
 *
 * <p><b>鉴权（安全声明）</b>：契约要求全 {@code /api/deployments/*} = AdminAuth（企业部署仅管理员，
 * 部分接口需 EnterpriseClient api_key）。本切片尚未落地 AdminAuth 过滤器；SecurityConfig 当前对非公开端点
 * 统一 {@code authenticated()}（默认 401，不裸奔）。AdminAuth 过滤器接入后无需改本控制器签名。</p>
 *
 * <p><b>客户视图铁律</b>：部署管理为管理端能力（AdminAuth），出参为运维所需的状态/时长/硬件摘要 +
 * 上游透传字段（已在 infra 层剔除 api_key 等敏感键），不含成本/利润/上游模型 B。</p>
 */
@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {

    private final ListDeploymentsUseCase listDeploymentsUseCase;
    private final SearchDeploymentsUseCase searchDeploymentsUseCase;
    private final GetDeploymentDetailUseCase getDeploymentDetailUseCase;
    private final CreateDeploymentUseCase createDeploymentUseCase;
    private final UpdateDeploymentUseCase updateDeploymentUseCase;
    private final RenameDeploymentUseCase renameDeploymentUseCase;
    private final ExtendDeploymentUseCase extendDeploymentUseCase;
    private final DeleteDeploymentUseCase deleteDeploymentUseCase;

    /**
     * @param listDeploymentsUseCase     部署列表用例（F-3041）
     * @param searchDeploymentsUseCase   部署搜索用例（F-3042）
     * @param getDeploymentDetailUseCase 部署详情用例（F-3043）
     * @param createDeploymentUseCase    创建部署用例（F-3044）
     * @param updateDeploymentUseCase    部署更新用例（F-3045）
     * @param renameDeploymentUseCase    部署重命名用例（F-3046）
     * @param extendDeploymentUseCase    部署续期用例（F-3047）
     * @param deleteDeploymentUseCase    删除部署用例（F-3048）
     */
    public DeploymentController(ListDeploymentsUseCase listDeploymentsUseCase,
                                SearchDeploymentsUseCase searchDeploymentsUseCase,
                                GetDeploymentDetailUseCase getDeploymentDetailUseCase,
                                CreateDeploymentUseCase createDeploymentUseCase,
                                UpdateDeploymentUseCase updateDeploymentUseCase,
                                RenameDeploymentUseCase renameDeploymentUseCase,
                                ExtendDeploymentUseCase extendDeploymentUseCase,
                                DeleteDeploymentUseCase deleteDeploymentUseCase) {
        this.listDeploymentsUseCase = listDeploymentsUseCase;
        this.searchDeploymentsUseCase = searchDeploymentsUseCase;
        this.getDeploymentDetailUseCase = getDeploymentDetailUseCase;
        this.createDeploymentUseCase = createDeploymentUseCase;
        this.updateDeploymentUseCase = updateDeploymentUseCase;
        this.renameDeploymentUseCase = renameDeploymentUseCase;
        this.extendDeploymentUseCase = extendDeploymentUseCase;
        this.deleteDeploymentUseCase = deleteDeploymentUseCase;
    }

    /**
     * 部署列表查询（F-3041，{@code GET /api/deployments/}）。
     *
     * <p>分页参数 {@code p}/{@code page_size} 非正/越界由 {@link Pagination} 归一（不报错）。
     * 出参含 items/status_counts/total/page/page_size。</p>
     *
     * @param page     query 页号（可空，缺省 1）
     * @param pageSize query 每页条数（可空，缺省 10，上限 100）
     * @return 成功信封，data = {@code { items[], status_counts, total, page, page_size }}
     */
    @GetMapping("/")
    public ApiResponse<DeploymentListView> list(
            @RequestParam(name = "p", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {

        Pagination pagination = Pagination.of(page, pageSize);
        return ApiResponse.okData(DeploymentListView.from(listDeploymentsUseCase.list(pagination)));
    }

    /**
     * 部署搜索（F-3042，{@code GET /api/deployments/search}）。
     *
     * <p>{@code status} 透传上游过滤；{@code keyword} 本地按名称小写包含过滤（空则不过滤），
     * 非空时 total 修正为过滤数量（领域聚合完成）。</p>
     *
     * @param status   query 状态过滤（可空，透传上游）
     * @param keyword  query 名称关键词（可空，本地过滤）
     * @param page     query 页号（可空，缺省 1）
     * @param pageSize query 每页条数（可空，缺省 10）
     * @return 成功信封，data = {@code { items[], status_counts, total, page, page_size }}
     */
    @GetMapping("/search")
    public ApiResponse<DeploymentListView> search(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "p", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {

        Pagination pagination = Pagination.of(page, pageSize);
        return ApiResponse.okData(
                DeploymentListView.from(searchDeploymentsUseCase.search(status, keyword, pagination)));
    }

    /**
     * 部署详情查询（F-3043，{@code GET /api/deployments/{id}}）。
     *
     * <p>{@code id} 必填（空由 {@link DeploymentId} 抛 400「deployment ID is required」）。</p>
     *
     * @param id path 部署 ID（必填）
     * @return 成功信封，data = 上游部署详情（已脱敏透传）
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable("id") String id) {
        return ApiResponse.okData(getDeploymentDetailUseCase.get(new DeploymentId(id)));
    }

    /**
     * 创建部署（F-3044，{@code POST /api/deployments/}）。
     *
     * <p>请求体透传上游 {@code ionet.DeploymentRequest}；非 JSON 对象由反序列化阶段拒绝（→400）。
     * 成功附 message「Deployment created successfully」。</p>
     *
     * @param request 创建请求体（透传上游）
     * @return 成功信封，message + data = {@code { deployment_id, status }}（已脱敏）
     */
    @PostMapping("/")
    public ApiResponse<Map<String, Object>> create(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        return ApiResponse.okData("Deployment created successfully", createDeploymentUseCase.create(body));
    }

    /**
     * 部署更新（F-3045，{@code PUT /api/deployments/{id}}）。
     *
     * <p>{@code id} 必填；请求体透传上游 {@code ionet.UpdateDeploymentRequest}（覆盖式更新）。</p>
     *
     * @param id      path 部署 ID（必填）
     * @param request 更新请求体（透传上游）
     * @return 成功信封，data = {@code { status, deployment_id }}（已脱敏）
     */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(
            @PathVariable("id") String id,
            @RequestBody(required = false) Map<String, Object> request) {

        DeploymentId deploymentId = new DeploymentId(id);
        Map<String, Object> body = request == null ? Map.of() : request;
        return ApiResponse.okData(updateDeploymentUseCase.update(deploymentId, body));
    }

    /**
     * 部署重命名（F-3046，{@code PUT /api/deployments/{id}/name}）。
     *
     * <p>{@code id} 必填；body {@code name} 必填（空→400「deployment name cannot be empty」）。
     * 名称可用性预检：不可用→409「name is not available」、预检失败→502「failed to check name availability」。</p>
     *
     * @param id      path 部署 ID（必填）
     * @param request 请求体（含必填 name）
     * @return 成功信封，data = {@code { success: true }}
     */
    @PutMapping("/{id}/name")
    public ApiResponse<Map<String, Object>> rename(
            @PathVariable("id") String id,
            @RequestBody(required = false) Map<String, Object> request) {

        DeploymentId deploymentId = new DeploymentId(id);
        // name 缺失/非字符串归一为 null，由 DeploymentName 抛「deployment name cannot be empty」（400）。
        String rawName = (request == null || request.get("name") == null)
                ? null : String.valueOf(request.get("name"));
        DeploymentName name = new DeploymentName(rawName);
        renameDeploymentUseCase.rename(deploymentId, name);
        // 契约 F-3046 出参固定 { success: true }。
        return ApiResponse.okData(Map.of("success", true));
    }

    /**
     * 部署续期（F-3047，{@code POST /api/deployments/{id}/extend}）。
     *
     * <p>{@code id} 必填；请求体透传上游 {@code ionet.ExtendDurationRequest}。</p>
     *
     * @param id      path 部署 ID（必填）
     * @param request 续期请求体（透传上游）
     * @return 成功信封，data = {@code { compute_minutes_remaining, time_remaining }}（已脱敏）
     */
    @PostMapping("/{id}/extend")
    public ApiResponse<Map<String, Object>> extend(
            @PathVariable("id") String id,
            @RequestBody(required = false) Map<String, Object> request) {

        DeploymentId deploymentId = new DeploymentId(id);
        Map<String, Object> body = request == null ? Map.of() : request;
        return ApiResponse.okData(extendDeploymentUseCase.extend(deploymentId, body));
    }

    /**
     * 删除/终止部署（F-3048，{@code DELETE /api/deployments/{id}}）。
     *
     * <p>{@code id} 必填。成功附 message「Deployment termination requested successfully」。</p>
     *
     * @param id path 部署 ID（必填）
     * @return 成功信封，message + data = {@code { status, deployment_id }}（已脱敏）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable("id") String id) {
        DeploymentId deploymentId = new DeploymentId(id);
        return ApiResponse.okData(
                "Deployment termination requested successfully", deleteDeploymentUseCase.delete(deploymentId));
    }
}
