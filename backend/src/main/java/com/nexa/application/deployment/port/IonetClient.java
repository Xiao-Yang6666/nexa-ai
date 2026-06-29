package com.nexa.application.deployment.port;

import com.nexa.domain.deployment.model.ConnectionTestResult;
import com.nexa.domain.deployment.model.ContainerList;
import com.nexa.domain.deployment.model.DeploymentList;
import com.nexa.domain.deployment.model.HardwareCatalog;
import com.nexa.domain.deployment.model.IntegrationStatus;
import com.nexa.domain.deployment.model.LocationCatalog;
import com.nexa.domain.deployment.model.NameAvailability;
import com.nexa.domain.deployment.vo.ClusterName;
import com.nexa.domain.deployment.vo.ContainerId;
import com.nexa.domain.deployment.vo.DeploymentId;
import com.nexa.domain.deployment.vo.DeploymentName;
import com.nexa.domain.deployment.vo.GpuCount;
import com.nexa.domain.deployment.vo.HardwareId;
import com.nexa.domain.deployment.vo.LogQuery;
import com.nexa.domain.deployment.vo.Pagination;

import java.util.Map;

/**
 * io.net 企业部署上游网关端口（应用层 / 防腐层接口）。
 *
 * <p>DDD 铁律：domain/application 只依赖本端口，不依赖具体 HTTP client / io.net SDK
 * （backend-engineer §2.3）。基础设施层（{@code IonetClientImpl}）实现本接口，封装上游端点 URL、
 * api_key 鉴权、字段映射、错误归一。这样部署用例可脱离真实上游单测（mock 本端口）。</p>
 *
 * <p>方法分两类返回形态：</p>
 * <ul>
 *   <li><b>强类型领域模型</b>（硬件目录 / 地域目录 / 容器列表 / 名称可用性）：需在领域层做聚合计算
 *       （total/total_available 等），故映射为领域模型。</li>
 *   <li><b>原始透传 Map</b>（可用副本 / 价格预估 / 容器详情 / 容器日志）：契约明确「data = 上游响应」
 *       （API-ENDPOINTS §10.3/§10.4），且上游结构繁杂/易变、本系统不二次解释（铁律：实际计费以
 *       io.net 为准），故原样透传上游 JSON，仅做敏感键剔除（在 infra 实现内）。</li>
 * </ul>
 *
 * <p>所有方法在上游未配置/连接失败/返回 APIError 时抛
 * {@link com.nexa.domain.deployment.exception.IonetIntegrationException}（接口层映射 502）。</p>
 */
public interface IonetClient {

    /**
     * 查询硬件类型目录（F-3049，EnterpriseClient）。
     *
     * @return 硬件类型聚合（含派生 total/total_available）
     */
    HardwareCatalog listHardwareTypes();

    /**
     * 查询部署地域目录（F-3050，普通 Client）。
     *
     * @return 地域聚合（含上游 total 与列表长度兜底）
     */
    LocationCatalog listLocations();

    /**
     * 查询指定硬件与 GPU 数下的可用副本（F-3051，EnterpriseClient）。
     *
     * <p>上游响应结构原样透传（契约 data=可用副本响应）。</p>
     *
     * @param hardwareId 硬件类型 ID（已校验 &gt;0）
     * @param gpuCount   每副本 GPU 数（已归一 &gt;=1）
     * @return 上游可用副本响应（原始 JSON 映射）
     */
    Map<String, Object> queryAvailableReplicas(HardwareId hardwareId, GpuCount gpuCount);

    /**
     * 部署价格预估（F-3052）。
     *
     * <p>上游 priceResp 原样透传（契约铁律：仅参考，实际计费以 io.net 为准）。</p>
     *
     * @param request 价格预估请求体（透传上游 ionet.PriceEstimationRequest）
     * @return 上游价格预估响应（原始 JSON 映射）
     */
    Map<String, Object> estimatePrice(Map<String, Object> request);

    /**
     * 集群名称可用性查询（F-3053）。
     *
     * @param name 集群名称（已校验非空）
     * @return 名称可用性结果（available + 原样回带 name）
     */
    NameAvailability checkClusterName(ClusterName name);

    /**
     * 查询部署的容器列表（含事件，F-3054，EnterpriseClient）。
     *
     * @param deploymentId 部署 ID（已校验非空）
     * @return 容器列表聚合（含派生 total；无容器→空列表）
     */
    ContainerList listContainers(DeploymentId deploymentId);

    /**
     * 查询单个容器详情（F-3055）。
     *
     * <p>上游 details 原样透传。details 为空时由实现抛
     * {@link com.nexa.domain.deployment.exception.IonetIntegrationException}（契约「container details not found」）。</p>
     *
     * @param deploymentId 部署 ID（已校验非空）
     * @param containerId  容器 ID（已校验非空）
     * @return 上游容器详情（原始 JSON 映射）
     */
    Map<String, Object> getContainerDetail(DeploymentId deploymentId, ContainerId containerId);

    /**
     * 查询容器日志（按级别/流/游标分页 + 时间范围，F-3056，普通 Client）。
     *
     * @param deploymentId 部署 ID（已校验非空）
     * @param containerId  容器 ID（已校验非空）
     * @param query        日志查询条件（已归一 limit/时间）
     * @return 上游原始日志响应（原始 JSON 映射）
     */
    Map<String, Object> getContainerLogs(DeploymentId deploymentId, ContainerId containerId, LogQuery query);

    // ---------------------------------------------------------------------
    // 集成开关与连接（F-3039/F-3040）
    // ---------------------------------------------------------------------

    /**
     * 查询 io.net 集成状态（F-3039，无上游调用）。
     *
     * <p>纯由本地配置（Option {@code model_deployment.ionet.*}）派生：{@code enabled}/{@code configured}，
     * {@code can_connect} 由领域聚合派生。本方法<b>不发上游请求</b>（契约 F-3039：设置查询本身不触发连接），
     * 故 api_key 缺失不报错，只如实反映 configured=false。</p>
     *
     * @return io.net 集成状态聚合
     */
    IntegrationStatus getIntegrationStatus();

    /**
     * io.net 连接测试（F-3040，EnterpriseClient）。
     *
     * <p>用给定 api_key（为空则回退已配置 stored key）校验连接：通过拉取硬件类型目录验证 key 有效性，
     * 并返回硬件总数与总可用量。key 与 stored key 均为空时抛
     * {@link com.nexa.domain.deployment.exception.IonetIntegrationException}（契约「api_key is required」）；
     * key 无效时抛同类异常透传上游 {@code APIError.Message}（空回退「failed to validate api key」）。</p>
     *
     * @param apiKeyOverride 临时 api_key（可空，空则回退 stored key）
     * @return 连接测试结果（hardware_count + total_available）
     */
    ConnectionTestResult testConnection(String apiKeyOverride);

    // ---------------------------------------------------------------------
    // 部署 CRUD 与运维（F-3041~F-3048）
    // ---------------------------------------------------------------------

    /**
     * 查询部署列表（F-3041，EnterpriseClient）。
     *
     * <p>按分页拉取上游部署列表，映射为强类型概要列表（供领域层派生 status_counts）。
     * 上游 {@code SortBy=created_at desc}。</p>
     *
     * @param pagination 归一后的分页参数
     * @return 部署列表聚合（含 items/total/page/page_size，status_counts 由领域派生）
     */
    DeploymentList listDeployments(Pagination pagination);

    /**
     * 搜索部署（F-3042，EnterpriseClient）。
     *
     * <p>{@code status} 作为上游过滤参数透传（可空）；返回的列表由调用方（用例）再做名称关键词本地过滤。
     * 本方法只负责「带 status 过滤的上游拉取」，关键词过滤是领域行为不在 infra 做。</p>
     *
     * @param status     上游状态过滤参数（可空透传）
     * @param pagination 归一后的分页参数
     * @return 部署列表聚合（status 过滤后、关键词过滤前）
     */
    DeploymentList searchDeployments(String status, Pagination pagination);

    /**
     * 查询部署详情（F-3043）。
     *
     * <p>上游详情原样透传（契约 data = { total_gpus, total_containers, ..., container_config }）。</p>
     *
     * @param deploymentId 部署 ID（已校验非空）
     * @return 上游部署详情（原始 JSON 映射，已脱敏）
     */
    Map<String, Object> getDeploymentDetail(DeploymentId deploymentId);

    /**
     * 创建部署（F-3044，EnterpriseClient）。
     *
     * <p>请求体透传上游 {@code ionet.DeploymentRequest}；上游侧生成 deployment_id（无幂等键）。</p>
     *
     * @param request 创建请求体（透传上游）
     * @return 上游创建结果（含 deployment_id/status，已脱敏）
     */
    Map<String, Object> createDeployment(Map<String, Object> request);

    /**
     * 更新部署配置（F-3045，覆盖式更新）。
     *
     * <p>请求体透传上游 {@code ionet.UpdateDeploymentRequest}；以 deployment_id 为幂等键。</p>
     *
     * @param deploymentId 部署 ID（已校验非空）
     * @param request      更新请求体（透传上游）
     * @return 上游更新结果（含 status/deployment_id，已脱敏）
     */
    Map<String, Object> updateDeployment(DeploymentId deploymentId, Map<String, Object> request);

    /**
     * 部署重命名（F-3046，含名称可用性预检）。
     *
     * <p>提交重命名请求；上游侧已做名称可用性校验。名称不可用时抛
     * {@link com.nexa.domain.deployment.exception.DeploymentNameUnavailableException}（→409）；
     * 预检/上游失败抛 {@link com.nexa.domain.deployment.exception.IonetIntegrationException}
     * （契约「failed to check name availability」，→502）。成功返回。</p>
     *
     * @param deploymentId 部署 ID（已校验非空）
     * @param name         新名称（已校验非空）
     */
    void renameDeployment(DeploymentId deploymentId, DeploymentName name);

    /**
     * 部署续期（F-3047，延长计算时长）。
     *
     * <p>请求体透传上游 {@code ionet.ExtendDurationRequest}；返回更新后的剩余计算分钟/剩余时间。</p>
     *
     * @param deploymentId 部署 ID（已校验非空）
     * @param request      续期请求体（透传上游）
     * @return 上游续期结果（含 compute_minutes_remaining/time_remaining，已脱敏）
     */
    Map<String, Object> extendDeployment(DeploymentId deploymentId, Map<String, Object> request);

    /**
     * 删除/终止部署（F-3048，请求上游终止）。
     *
     * @param deploymentId 部署 ID（已校验非空）
     * @return 上游终止结果（含 status/deployment_id，已脱敏）
     */
    Map<String, Object> deleteDeployment(DeploymentId deploymentId);
}
