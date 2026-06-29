package com.nexa.interfaces.deployment.api;

import com.nexa.application.deployment.GetContainerDetailUseCase;
import com.nexa.application.deployment.GetContainerLogsUseCase;
import com.nexa.application.deployment.ListContainersUseCase;
import com.nexa.domain.deployment.vo.ContainerId;
import com.nexa.domain.deployment.vo.DeploymentId;
import com.nexa.domain.deployment.vo.LogQuery;
import com.nexa.common.web.ApiResponse;
import com.nexa.interfaces.deployment.api.dto.ContainerListVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 部署容器查询控制器（AdminAuth 端点，接口层，F-3054~F-3056）。
 *
 * <p>承载部署管理「容器查询」端点（对齐 API-ENDPOINTS §10.4）：
 * <ul>
 *   <li>{@code GET /api/deployments/{id}/containers}                          容器列表（含事件，F-3054）</li>
 *   <li>{@code GET /api/deployments/{id}/containers/{container_id}}           容器详情（F-3055）</li>
 *   <li>{@code GET /api/deployments/{id}/containers/{container_id}/logs}      容器日志（F-3056）</li>
 * </ul>
 * </p>
 *
 * <p>DDD 铁律：接口层只做协议翻译。id/container_id 非空校验在值对象构造点
 * （{@link DeploymentId}/{@link ContainerId}）；日志 limit 截断、时间宽松解析在 {@link LogQuery} 归一；
 * 容器列表 total 由领域聚合派生；上游交互/「container details not found」判定在 infra
 * （backend-engineer §2.1/§2.4）。</p>
 *
 * <p><b>鉴权（安全声明）</b>：契约要求全 {@code /api/deployments/*} = AdminAuth；本切片尚未落地
 * AdminAuth 过滤器，SecurityConfig 当前对非公开端点统一 {@code authenticated()}（默认 401，不裸奔）。</p>
 */
@RestController
@RequestMapping("/api/deployments")
public class ContainerController {

    private final ListContainersUseCase listContainersUseCase;
    private final GetContainerDetailUseCase getContainerDetailUseCase;
    private final GetContainerLogsUseCase getContainerLogsUseCase;

    /**
     * @param listContainersUseCase     容器列表用例（F-3054）
     * @param getContainerDetailUseCase 容器详情用例（F-3055）
     * @param getContainerLogsUseCase   容器日志用例（F-3056）
     */
    public ContainerController(ListContainersUseCase listContainersUseCase,
                               GetContainerDetailUseCase getContainerDetailUseCase,
                               GetContainerLogsUseCase getContainerLogsUseCase) {
        this.listContainersUseCase = listContainersUseCase;
        this.getContainerDetailUseCase = getContainerDetailUseCase;
        this.getContainerLogsUseCase = getContainerLogsUseCase;
    }

    /**
     * 部署容器列表（F-3054，{@code GET /api/deployments/{id}/containers}）。
     *
     * <p>{@code id} 必填（空由 {@link DeploymentId} 抛 400「deployment ID is required」）。
     * 无容器→空数组 + total=0。</p>
     *
     * @param id path 部署 ID（必填）
     * @return 成功信封，data = {@code { containers[], total }}
     */
    @GetMapping("/{id}/containers")
    public ApiResponse<ContainerListVO> containers(@PathVariable("id") String id) {
        DeploymentId deploymentId = new DeploymentId(id);
        return ApiResponse.okData(ContainerListVO.from(listContainersUseCase.list(deploymentId)));
    }

    /**
     * 容器详情查询（F-3055，{@code GET /api/deployments/{id}/containers/{container_id}}）。
     *
     * <p>{@code id}/{@code container_id} 任一为空抛对应必填错误（→400）；上游 details 为空抛
     * 「container details not found」（→502，由 infra 判定）。</p>
     *
     * @param id          path 部署 ID（必填）
     * @param containerId path 容器 ID（必填）
     * @return 成功信封，data = 上游容器详情（已脱敏透传）
     */
    @GetMapping("/{id}/containers/{container_id}")
    public ApiResponse<Map<String, Object>> containerDetail(
            @PathVariable("id") String id,
            @PathVariable("container_id") String containerId) {

        DeploymentId deploymentId = new DeploymentId(id);
        ContainerId cid = ContainerId.forDetail(containerId);
        return ApiResponse.okData(getContainerDetailUseCase.get(deploymentId, cid));
    }

    /**
     * 容器日志查询（F-3056，{@code GET /api/deployments/{id}/containers/{container_id}/logs}）。
     *
     * <p>{@code container_id} query 参数必填（空→400「container_id parameter is required」）；
     * path {@code id} 与 path {@code container_id} 同样必填。{@code limit} 缺省 100、上限 1000（&gt;1000 截断）；
     * {@code level}/{@code stream}/{@code cursor}/{@code follow} 透传；{@code start_time}/{@code end_time} 按
     * RFC3339 宽松解析（非法忽略）。归一规则全在 {@link LogQuery}。</p>
     *
     * @param id            path 部署 ID（必填）
     * @param pathContainerId path 容器 ID（必填）
     * @param containerId   query 容器 ID（契约必填，按 §10.4 单独校验）
     * @param limit         query 返回条数（可空，缺省 100，上限 1000）
     * @param level         query 日志级别过滤（可空）
     * @param stream        query 日志流过滤（可空）
     * @param cursor        query 分页游标（可空）
     * @param follow        query 是否流式跟随（可空，缺省 false）
     * @param startTime     query 起始时间 RFC3339（可空，非法忽略）
     * @param endTime       query 结束时间 RFC3339（可空，非法忽略）
     * @return 成功信封，data = 上游原始日志（已脱敏透传）
     */
    @GetMapping("/{id}/containers/{container_id}/logs")
    public ApiResponse<Map<String, Object>> containerLogs(
            @PathVariable("id") String id,
            @PathVariable("container_id") String pathContainerId,
            @RequestParam(name = "container_id", required = false) String containerId,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "level", required = false) String level,
            @RequestParam(name = "stream", required = false) String stream,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "follow", required = false) Boolean follow,
            @RequestParam(name = "start_time", required = false) String startTime,
            @RequestParam(name = "end_time", required = false) String endTime) {

        DeploymentId deploymentId = new DeploymentId(id);
        // 契约 F-3056：container_id 以 query 参数为权威必填来源（缺失→parameter is required）；
        // path 段 container_id 仅做路由占位，query 缺失时回退 path（兼容两种调用形态）。
        String effectiveContainerId = (containerId == null || containerId.isBlank())
                ? pathContainerId : containerId;
        ContainerId cid = ContainerId.forLogs(effectiveContainerId);
        LogQuery query = LogQuery.of(limit, level, stream, cursor, follow, startTime, endTime);
        return ApiResponse.okData(getContainerLogsUseCase.get(deploymentId, cid, query));
    }
}
