package com.nexa.deployment.application;

import com.nexa.deployment.application.port.IonetClient;
import com.nexa.deployment.domain.vo.DeploymentId;
import com.nexa.deployment.domain.vo.DeploymentName;
import org.springframework.stereotype.Service;

/**
 * 部署重命名用例（应用服务，F-3046）。
 *
 * <p>编排「部署重命名（名称可用性预检）」（API-ENDPOINTS §10.2 PUT /api/deployments/{id}/name）。
 * id 非空校验由 {@link DeploymentId} 守护；新名称非空校验由 {@link DeploymentName} 守护
 * （空→「deployment name cannot be empty」，接口层 400）。名称可用性预检 + 重命名提交在 infra：
 * 名称不可用→「name is not available」（409）、预检失败→「failed to check name availability」（502）。</p>
 */
@Service
public class RenameDeploymentUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口
     */
    public RenameDeploymentUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行部署重命名（含名称可用性预检）。
     *
     * @param deploymentId 部署 ID（已校验非空）
     * @param name         新名称（已校验非空）
     */
    public void rename(DeploymentId deploymentId, DeploymentName name) {
        ionetClient.renameDeployment(deploymentId, name);
    }
}
