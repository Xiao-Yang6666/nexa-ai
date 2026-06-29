package com.nexa.application.deployment;

import com.nexa.application.deployment.port.IonetClient;
import com.nexa.domain.deployment.model.LocationCatalog;
import org.springframework.stereotype.Service;

/**
 * 部署地域查询用例（应用服务，F-3050）。
 *
 * <p>编排「部署地域查询」（API-ENDPOINTS §10.3 GET /api/deployments/locations）。本类<b>薄</b>：
 * 转交上游端口拉取地域目录，total 的「上游 0 时回退列表长度」兜底由领域模型
 * {@link LocationCatalog} 自守护。</p>
 */
@Service
public class ListLocationsUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口
     */
    public ListLocationsUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行地域查询。
     *
     * @return 地域聚合（含 total 兜底）
     */
    public LocationCatalog list() {
        return ionetClient.listLocations();
    }
}
