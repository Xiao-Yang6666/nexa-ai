package com.nexa.deployment.application;

import com.nexa.deployment.application.port.IonetClient;
import com.nexa.deployment.domain.vo.DeploymentId;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 删除/终止部署用例（应用服务，F-3048）。
 *
 * <p>编排「删除/终止部署」（API-ENDPOINTS §10.2 DELETE /api/deployments/{id}）。id 非空校验由
 * {@link DeploymentId} 守护；向上游请求终止部署集群。出参为上游终止结果
 * （含 status/deployment_id，已脱敏），message「Deployment termination requested successfully」由接口层附加。</p>
 */
@Service
public class DeleteDeploymentUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口
     */
    public DeleteDeploymentUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行删除/终止部署。
     *
     * @param deploymentId 部署 ID（已校验非空）
     * @return 上游终止结果（含 status/deployment_id，已脱敏）
     */
    public Map<String, Object> delete(DeploymentId deploymentId) {
        return ionetClient.deleteDeployment(deploymentId);
    }
}
