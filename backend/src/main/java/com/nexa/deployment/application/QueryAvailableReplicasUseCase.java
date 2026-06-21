package com.nexa.deployment.application;

import com.nexa.deployment.application.port.IonetClient;
import com.nexa.deployment.domain.vo.GpuCount;
import com.nexa.deployment.domain.vo.HardwareId;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 可用副本查询用例（应用服务，F-3051）。
 *
 * <p>编排「可用副本查询（按硬件 ID + GPU 数）」（API-ENDPOINTS §10.3 GET /api/deployments/replicas）。
 * 入参校验（hardware_id 必填且 &gt;0、gpu_count 非正回退 1）由值对象
 * {@link HardwareId}/{@link GpuCount} 在构造点守护——用例不重复写 if（backend-engineer §2.4）。</p>
 */
@Service
public class QueryAvailableReplicasUseCase {

    private final IonetClient ionetClient;

    /**
     * @param ionetClient io.net 上游端口
     */
    public QueryAvailableReplicasUseCase(IonetClient ionetClient) {
        this.ionetClient = ionetClient;
    }

    /**
     * 执行可用副本查询。
     *
     * @param hardwareId 硬件类型 ID（已校验）
     * @param gpuCount   每副本 GPU 数（已归一）
     * @return 上游可用副本响应（原始透传）
     */
    public Map<String, Object> query(HardwareId hardwareId, GpuCount gpuCount) {
        return ionetClient.queryAvailableReplicas(hardwareId, gpuCount);
    }
}
