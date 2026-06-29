package com.nexa.application.deployment;

import com.nexa.application.deployment.port.IonetClient;
import com.nexa.domain.deployment.vo.DeploymentId;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 部署更新用例（应用服务，F-3045）。
 *
 * <p>编排「部署更新（配置变更）」（API-ENDPOINTS §10.2 PUT /api/deployments/{id}）。id 非空校验由
 * {@link DeploymentId} 守护；请求体透传上游 {@code ionet.UpdateDeploymentRequest}（覆盖式更新，
 * 幂等键 deployment_id）。出参为上游更新结果（含 status/deployment_id，已脱敏）。</p>
 */
@Service
public class UpdateDeploymentUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口
     */
    public UpdateDeploymentUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行部署更新。
     *
     * @param deploymentId 部署 ID（已校验非空）
     * @param request      更新请求体（透传上游）
     * @return 上游更新结果（含 status/deployment_id，已脱敏）
     */
    public Map<String, Object> update(DeploymentId deploymentId, Map<String, Object> request) {
        return ionetClient.updateDeployment(deploymentId, request);
    }
}
