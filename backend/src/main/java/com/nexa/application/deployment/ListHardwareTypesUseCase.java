package com.nexa.application.deployment;

import com.nexa.application.deployment.port.IonetClient;
import com.nexa.domain.deployment.model.HardwareCatalog;
import org.springframework.stereotype.Service;

/**
 * 硬件类型查询用例（应用服务，F-3049）。
 *
 * <p>编排「硬件类型查询（含总可用量统计）」（API-ENDPOINTS §10.3 GET /api/deployments/hardware-types）。
 * 本类<b>薄</b>：转交上游端口拉取硬件目录，total/total_available 的聚合计算由领域模型
 * {@link HardwareCatalog} 自守护（backend-engineer §2.2）。</p>
 */
@Service
public class ListHardwareTypesUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口（domain/application 仅依赖接口）
     */
    public ListHardwareTypesUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行硬件类型查询。
     *
     * @return 硬件类型聚合（含派生 total/total_available）
     */
    public HardwareCatalog list() {
        return ionetClient.listHardwareTypes();
    }
}
