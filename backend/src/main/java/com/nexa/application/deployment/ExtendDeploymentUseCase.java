package com.nexa.application.deployment;

import com.nexa.application.deployment.port.IonetClient;
import com.nexa.domain.deployment.vo.DeploymentId;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 部署续期用例（应用服务，F-3047）。
 *
 * <p>编排「部署续期（延长计算时长）」（API-ENDPOINTS §10.2 POST /api/deployments/{id}/extend）。
 * id 非空校验由 {@link DeploymentId} 守护；请求体透传上游 {@code ionet.ExtendDurationRequest}。
 * 出参为续期后状态（含 compute_minutes_remaining/time_remaining，已脱敏）。</p>
 */
@Service
public class ExtendDeploymentUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口
     */
    public ExtendDeploymentUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行部署续期。
     *
     * @param deploymentId 部署 ID（已校验非空）
     * @param request      续期请求体（透传上游）
     * @return 上游续期结果（含 compute_minutes_remaining/time_remaining，已脱敏）
     */
    public Map<String, Object> extend(DeploymentId deploymentId, Map<String, Object> request) {
        return ionetClient.extendDeployment(deploymentId, request);
    }
}
