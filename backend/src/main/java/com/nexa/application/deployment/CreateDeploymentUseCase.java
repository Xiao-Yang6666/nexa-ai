package com.nexa.application.deployment;

import com.nexa.application.deployment.port.IonetClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 创建部署用例（应用服务，F-3044）。
 *
 * <p>编排「创建部署（下发容器）」（API-ENDPOINTS §10.2 POST /api/deployments/）。请求体透传上游
 * {@code ionet.DeploymentRequest}；无幂等键（io.net 侧生成 deployment_id）。请求体非法（非 JSON 对象）
 * 由反序列化阶段拒绝（接口层 400）。出参为上游创建结果（含 deployment_id/status，已脱敏）。</p>
 */
@Service
public class CreateDeploymentUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口
     */
    public CreateDeploymentUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行创建部署。
     *
     * @param request 创建请求体（透传上游）
     * @return 上游创建结果（含 deployment_id/status，已脱敏）
     */
    public Map<String, Object> create(Map<String, Object> request) {
        return ionetClient.createDeployment(request);
    }
}
