package com.nexa.application.deployment;

import com.nexa.application.deployment.port.IonetClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 部署价格预估用例（应用服务，F-3052）。
 *
 * <p>编排「部署价格预估」（API-ENDPOINTS §10.3 POST /api/deployments/price-estimation）。
 * 请求体透传上游 {@code ionet.PriceEstimationRequest}，响应 priceResp 原样透传。本类<b>薄</b>：
 * 不二次解释价格（铁律：实际计费以 io.net 为准，价格预估仅参考）。</p>
 */
@Service
public class EstimatePriceUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口
     */
    public EstimatePriceUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行价格预估。
     *
     * @param request 价格预估请求体（透传上游）
     * @return 上游 priceResp（原始透传）
     */
    public Map<String, Object> estimate(Map<String, Object> request) {
        return ionetClient.estimatePrice(request);
    }
}
