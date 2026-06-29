package com.nexa.application.deployment;

import com.nexa.application.deployment.port.IonetClient;
import com.nexa.domain.deployment.vo.DeploymentId;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 部署详情查询用例（应用服务，F-3043）。
 *
 * <p>编排「部署详情查询」（API-ENDPOINTS §10.2 GET /api/deployments/{id}）。id 非空校验由值对象
 * {@link DeploymentId} 守护（空→「deployment ID is required」）。出参为上游详情原样透传
 * （已脱敏，data = { total_gpus, total_containers, ..., container_config }）。</p>
 */
@Service
public class GetDeploymentDetailUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口
     */
    public GetDeploymentDetailUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行部署详情查询。
     *
     * @param deploymentId 部署 ID（已校验非空）
     * @return 上游部署详情（原始 JSON 映射，已脱敏）
     */
    public Map<String, Object> get(DeploymentId deploymentId) {
        return ionetClient.getDeploymentDetail(deploymentId);
    }
}
