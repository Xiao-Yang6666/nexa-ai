package com.nexa.application.deployment;

import com.nexa.application.deployment.port.IonetClient;
import com.nexa.domain.deployment.vo.ContainerId;
import com.nexa.domain.deployment.vo.DeploymentId;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 容器详情查询用例（应用服务，F-3055）。
 *
 * <p>编排「容器详情查询」（API-ENDPOINTS §10.4 GET /api/deployments/:id/containers/:container_id）。
 * id/container_id 任一为空抛对应必填错误（由值对象 {@link DeploymentId}/{@link ContainerId} 守护）；
 * 上游 details 为空抛集成异常（「container details not found」，在 infra 实现内判定）。响应原样透传。</p>
 */
@Service
public class GetContainerDetailUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口
     */
    public GetContainerDetailUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行容器详情查询。
     *
     * @param deploymentId 部署 ID（已校验非空）
     * @param containerId  容器 ID（已校验非空）
     * @return 上游容器详情（原始透传）
     */
    public Map<String, Object> get(DeploymentId deploymentId, ContainerId containerId) {
        return ionetClient.getContainerDetail(deploymentId, containerId);
    }
}
