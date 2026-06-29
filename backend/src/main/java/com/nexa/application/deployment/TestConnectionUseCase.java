package com.nexa.application.deployment;

import com.nexa.application.deployment.port.IonetClient;
import com.nexa.domain.deployment.model.ConnectionTestResult;
import org.springframework.stereotype.Service;

/**
 * io.net 连接测试用例（应用服务，F-3040）。
 *
 * <p>编排「io.net 连接测试」（API-ENDPOINTS §10.1 POST /api/deployments/test）。校验给定 api_key
 * （为空回退 stored key）的有效性并返回硬件可用量。key 缺失（含 stored）→「api_key is required」、
 * key 无效→上游 APIError（空回退「failed to validate api key」），均由 infra 层抛集成异常
 * （接口层映射 502）。</p>
 */
@Service
public class TestConnectionUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口
     */
    public TestConnectionUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行连接测试。
     *
     * @param apiKeyOverride 临时 api_key（可空，空则回退 stored key）
     * @return 连接测试结果（hardware_count + total_available）
     */
    public ConnectionTestResult test(String apiKeyOverride) {
        return ionetClient.testConnection(apiKeyOverride);
    }
}
