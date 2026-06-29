package com.nexa.application.deployment;

import com.nexa.application.deployment.port.IonetClient;
import com.nexa.domain.deployment.model.DeploymentList;
import com.nexa.domain.deployment.vo.Pagination;
import org.springframework.stereotype.Service;

/**
 * 部署搜索用例（应用服务，F-3042）。
 *
 * <p>编排「部署搜索」（API-ENDPOINTS §10.2 GET /api/deployments/search）：{@code status} 作为上游过滤
 * 参数透传；{@code keyword} 在领域层做名称小写包含的<b>本地</b>过滤（{@link DeploymentList#filterByKeyword}），
 * 过滤后 {@code total} 修正为过滤数量。本用例编排「先上游 status 过滤拉取，再领域本地关键词过滤」两步。</p>
 */
@Service
public class SearchDeploymentsUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口
     */
    public SearchDeploymentsUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行部署搜索（上游 status 过滤 + 领域本地关键词过滤）。
     *
     * @param status     上游状态过滤参数（可空透传）
     * @param keyword    名称关键词（可空，空则不本地过滤）
     * @param pagination 归一后的分页参数
     * @return 关键词过滤后的部署列表聚合（keyword 非空时 total=过滤数量）
     */
    public DeploymentList search(String status, String keyword, Pagination pagination) {
        DeploymentList upstream = ionetClient.searchDeployments(status, pagination);
        // 名称关键词本地过滤是领域行为（契约 F-3042），由聚合自身完成并修正 total。
        return upstream.filterByKeyword(keyword);
    }
}
