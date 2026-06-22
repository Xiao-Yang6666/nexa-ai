package com.nexa.deployment.application;

import com.nexa.deployment.application.port.IonetClient;
import com.nexa.deployment.domain.model.ContainerList;
import com.nexa.deployment.domain.vo.DeploymentId;
import org.springframework.stereotype.Service;

/**
 * 部署容器列表查询用例（应用服务，F-3054）。
 *
 * <p>编排「部署容器列表（含容器事件）」（API-ENDPOINTS §10.4 GET /api/deployments/:id/containers）。
 * deployment id 非空校验由值对象 {@link DeploymentId} 守护；total 由聚合
 * {@link ContainerList} 派生（无容器→空数组 + total=0）。</p>
 */
@Service
public class ListContainersUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口
     */
    public ListContainersUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行容器列表查询。
     *
     * @param deploymentId 部署 ID（已校验非空）
     * @return 容器列表聚合（含派生 total）
     */
    public ContainerList list(DeploymentId deploymentId) {
        return ionetClient.listContainers(deploymentId);
    }
}
