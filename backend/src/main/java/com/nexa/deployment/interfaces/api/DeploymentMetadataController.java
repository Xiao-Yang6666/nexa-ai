package com.nexa.deployment.interfaces.api;

import com.nexa.deployment.application.CheckClusterNameUseCase;
import com.nexa.deployment.application.EstimatePriceUseCase;
import com.nexa.deployment.application.ListHardwareTypesUseCase;
import com.nexa.deployment.application.ListLocationsUseCase;
import com.nexa.deployment.application.QueryAvailableReplicasUseCase;
import com.nexa.deployment.domain.vo.ClusterName;
import com.nexa.deployment.domain.vo.GpuCount;
import com.nexa.deployment.domain.vo.HardwareId;
import com.nexa.deployment.interfaces.api.dto.ApiResponse;
import com.nexa.deployment.interfaces.api.dto.HardwareTypesView;
import com.nexa.deployment.interfaces.api.dto.LocationsView;
import com.nexa.deployment.interfaces.api.dto.NameAvailabilityView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 部署元数据与价格控制器（AdminAuth 端点，接口层，F-3049~F-3053）。
 *
 * <p>承载部署管理「元数据与价格」端点（对齐 API-ENDPOINTS §10.3）：
 * <ul>
 *   <li>{@code GET  /api/deployments/hardware-types}  硬件类型查询（F-3049）</li>
 *   <li>{@code GET  /api/deployments/locations}        部署地域查询（F-3050）</li>
 *   <li>{@code GET  /api/deployments/replicas}         可用副本查询（F-3051）</li>
 *   <li>{@code POST /api/deployments/price-estimation} 部署价格预估（F-3052）</li>
 *   <li>{@code GET  /api/deployments/check-name}       集群名称可用性（F-3053）</li>
 * </ul>
 * </p>
 *
 * <p>DDD 铁律：接口层<b>只做协议翻译</b>（query/body ⇄ 值对象/用例 ⇄ 视图 DTO），不含业务逻辑——
 * 入参校验在值对象构造点（{@link HardwareId}/{@link GpuCount}/{@link ClusterName}），total/total_available
 * 聚合在领域模型，上游交互在 infra（backend-engineer §2.1）。参数错误（→400）与上游集成错误（→502）
 * 由 {@code DeploymentExceptionHandler} 统一翻译。</p>
 *
 * <p><b>鉴权（安全声明）</b>：契约要求全 {@code /api/deployments/*} = AdminAuth（企业部署仅管理员）。
 * 本切片<b>尚未</b>落地 AdminAuth 鉴权过滤器（与 com.nexa.account 管理端同状态）；SecurityConfig 当前对
 * 非公开端点统一要求 {@code authenticated()}，故这些端点不会无鉴权裸奔（默认 401）。AdminAuth 过滤器
 * 接入后由认证主体提供 admin 角色校验，无需改本控制器签名。</p>
 */
@RestController
@RequestMapping("/api/deployments")
public class DeploymentMetadataController {

    private final ListHardwareTypesUseCase listHardwareTypesUseCase;
    private final ListLocationsUseCase listLocationsUseCase;
    private final QueryAvailableReplicasUseCase queryAvailableReplicasUseCase;
    private final EstimatePriceUseCase estimatePriceUseCase;
    private final CheckClusterNameUseCase checkClusterNameUseCase;

    /**
     * @param listHardwareTypesUseCase     硬件类型查询用例（F-3049）
     * @param listLocationsUseCase         地域查询用例（F-3050）
     * @param queryAvailableReplicasUseCase 可用副本查询用例（F-3051）
     * @param estimatePriceUseCase         价格预估用例（F-3052）
     * @param checkClusterNameUseCase      名称可用性用例（F-3053）
     */
    public DeploymentMetadataController(ListHardwareTypesUseCase listHardwareTypesUseCase,
                                        ListLocationsUseCase listLocationsUseCase,
                                        QueryAvailableReplicasUseCase queryAvailableReplicasUseCase,
                                        EstimatePriceUseCase estimatePriceUseCase,
                                        CheckClusterNameUseCase checkClusterNameUseCase) {
        this.listHardwareTypesUseCase = listHardwareTypesUseCase;
        this.listLocationsUseCase = listLocationsUseCase;
        this.queryAvailableReplicasUseCase = queryAvailableReplicasUseCase;
        this.estimatePriceUseCase = estimatePriceUseCase;
        this.checkClusterNameUseCase = checkClusterNameUseCase;
    }

    /**
     * 硬件类型查询（F-3049，{@code GET /api/deployments/hardware-types}）。
     *
     * @return 成功信封，data = {@code { hardware_types[], total, total_available }}
     */
    @GetMapping("/hardware-types")
    public ApiResponse<HardwareTypesView> hardwareTypes() {
        return ApiResponse.okData(HardwareTypesView.from(listHardwareTypesUseCase.list()));
    }

    /**
     * 部署地域查询（F-3050，{@code GET /api/deployments/locations}）。
     *
     * @return 成功信封，data = {@code { locations[], total }}（total 上游 0 时回退列表长度）
     */
    @GetMapping("/locations")
    public ApiResponse<LocationsView> locations() {
        return ApiResponse.okData(LocationsView.from(listLocationsUseCase.list()));
    }

    /**
     * 可用副本查询（F-3051，{@code GET /api/deployments/replicas}）。
     *
     * <p>{@code hardware_id} 必填且 &gt;0（缺失/非法由 {@link HardwareId#parse} 抛 400）；
     * {@code gpu_count} 非正回退 1（由 {@link GpuCount#ofOrDefault} 归一）。</p>
     *
     * @param hardwareId query 硬件类型 ID（必填，原始字符串以便区分缺失/非法）
     * @param gpuCount   query 每副本 GPU 数（可空，缺省/非正回退 1）
     * @return 成功信封，data = 上游可用副本响应（已脱敏透传）
     */
    @GetMapping("/replicas")
    public ApiResponse<Map<String, Object>> replicas(
            @RequestParam(name = "hardware_id", required = false) String hardwareId,
            @RequestParam(name = "gpu_count", required = false) Integer gpuCount) {

        HardwareId hid = HardwareId.parse(hardwareId);
        GpuCount gpu = GpuCount.ofOrDefault(gpuCount);
        return ApiResponse.okData(queryAvailableReplicasUseCase.query(hid, gpu));
    }

    /**
     * 部署价格预估（F-3052，{@code POST /api/deployments/price-estimation}）。
     *
     * <p>请求体透传上游 {@code ionet.PriceEstimationRequest}（开放字段映射）；非 JSON 对象由
     * 反序列化阶段拒绝（→400 绑定错误）。出参为上游 priceResp（仅参考，铁律：实际计费以 io.net 为准）。</p>
     *
     * @param request 价格预估请求体（透传上游）
     * @return 成功信封，data = 上游 priceResp（已脱敏透传）
     */
    @PostMapping("/price-estimation")
    public ApiResponse<Map<String, Object>> priceEstimation(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        return ApiResponse.okData(estimatePriceUseCase.estimate(body));
    }

    /**
     * 集群名称可用性查询（F-3053，{@code GET /api/deployments/check-name}）。
     *
     * <p>{@code name} 必填（空由 {@link ClusterName} 抛 400「name parameter is required」）。</p>
     *
     * @param name query 集群名称（必填）
     * @return 成功信封，data = {@code { available, name }}
     */
    @GetMapping("/check-name")
    public ApiResponse<NameAvailabilityView> checkName(
            @RequestParam(name = "name", required = false) String name) {

        ClusterName clusterName = new ClusterName(name);
        return ApiResponse.okData(NameAvailabilityView.from(checkClusterNameUseCase.check(clusterName)));
    }
}
