package com.nexa.deployment.application;

import com.nexa.deployment.application.port.IonetClient;
import com.nexa.deployment.domain.model.IntegrationStatus;
import org.springframework.stereotype.Service;

/**
 * io.net 集成设置查询用例（应用服务，F-3039）。
 *
 * <p>编排「io.net 集成设置查询」（API-ENDPOINTS §10.1 GET /api/deployments/settings）。
 * 纯由本地配置派生（enabled/configured，can_connect 由领域聚合派生），<b>不触发上游连接</b>
 * （契约 F-3039：未启用或 key 缺失只在连接类接口报错，本接口如实反映状态）。</p>
 */
@Service
public class GetIntegrationStatusUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口（本用例仅取其本地集成状态，不发请求）
     */
    public GetIntegrationStatusUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 查询集成状态。
     *
     * @return io.net 集成状态聚合（provider/enabled/configured + 派生 can_connect）
     */
    public IntegrationStatus get() {
        return ionetClient.getIntegrationStatus();
    }
}
