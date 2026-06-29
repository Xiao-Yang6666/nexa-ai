package com.nexa.application.deployment;

import com.nexa.application.deployment.port.IonetClient;
import com.nexa.domain.deployment.model.DeploymentList;
import com.nexa.domain.deployment.vo.Pagination;
import org.springframework.stereotype.Service;

/**
 * 部署列表查询用例（应用服务，F-3041）。
 *
 * <p>编排「部署列表查询」（API-ENDPOINTS §10.2 GET /api/deployments/）：分页拉取上游部署列表，
 * 由领域聚合 {@link DeploymentList} 派生 {@code status_counts}。分页归一在值对象 {@link Pagination}。</p>
 */
@Service
public class ListDeploymentsUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口
     */
    public ListDeploymentsUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行部署列表查询。
     *
     * @param pagination 归一后的分页参数
     * @return 部署列表聚合（含 items/total/page/page_size，status_counts 由领域派生）
     */
    public DeploymentList list(Pagination pagination) {
        return ionetClient.listDeployments(pagination);
    }
}
