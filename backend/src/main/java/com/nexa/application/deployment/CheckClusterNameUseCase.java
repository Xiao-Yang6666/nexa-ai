package com.nexa.application.deployment;

import com.nexa.application.deployment.port.IonetClient;
import com.nexa.domain.deployment.model.NameAvailability;
import com.nexa.domain.deployment.vo.ClusterName;
import org.springframework.stereotype.Service;

/**
 * 集群名称可用性查询用例（应用服务，F-3053）。
 *
 * <p>编排「集群名称可用性查询」（API-ENDPOINTS §10.3 GET /api/deployments/check-name）。
 * name 非空校验由值对象 {@link ClusterName} 在构造点守护（空→「name parameter is required」）。</p>
 */
@Service
public class CheckClusterNameUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口
     */
    public CheckClusterNameUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行名称可用性查询。
     *
     * @param name 集群名称（已校验非空）
     * @return 名称可用性结果（available + 原样回带 name）
     */
    public NameAvailability check(ClusterName name) {
        return ionetClient.checkClusterName(name);
    }
}
