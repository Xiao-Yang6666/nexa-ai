package com.nexa.deployment.application;

import com.nexa.deployment.application.port.IonetClient;
import com.nexa.deployment.domain.vo.ContainerId;
import com.nexa.deployment.domain.vo.DeploymentId;
import com.nexa.deployment.domain.vo.LogQuery;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 容器日志查询用例（应用服务，F-3056）。
 *
 * <p>编排「容器日志查询（按级别/流/游标分页 + 时间范围）」
 * （API-ENDPOINTS §10.4 GET /api/deployments/:id/containers/:container_id/logs）。
 * container_id 必填校验由 {@link ContainerId#forLogs} 守护；limit 截断/时间宽松解析由
 * {@link LogQuery} 归一。上游原始日志原样透传。</p>
 */
@Service
public class GetContainerLogsUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口
     */
    public GetContainerLogsUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行容器日志查询。
     *
     * @param deploymentId 部署 ID（已校验非空）
     * @param containerId  容器 ID（已校验非空）
     * @param query        日志查询条件（已归一）
     * @return 上游原始日志响应（原始透传）
     */
    public Map<String, Object> get(DeploymentId deploymentId, ContainerId containerId, LogQuery query) {
        return ionetClient.getContainerLogs(deploymentId, containerId, query);
    }
}
