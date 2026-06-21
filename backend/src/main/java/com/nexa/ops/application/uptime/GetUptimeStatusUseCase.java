package com.nexa.ops.application.uptime;

import com.nexa.ops.application.port.UptimeStatusClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 公开状态查询用例（应用层，F-4026 GET /api/uptime/status 匿名公开）。
 *
 * <p>编排：委托 Uptime-Kuma 接入端口聚合各监控组状态。容错由端口实现承载（未配置→空列表、
 * 单组失败→该组空 monitors）。薄编排，无业务规则。</p>
 */
@Service
public class GetUptimeStatusUseCase {

    private final UptimeStatusClient uptimeStatusClient;

    /**
     * @param uptimeStatusClient Uptime-Kuma 接入端口
     */
    public GetUptimeStatusUseCase(UptimeStatusClient uptimeStatusClient) {
        this.uptimeStatusClient = uptimeStatusClient;
    }

    /**
     * 查询各监控组状态。
     *
     * @return 监控组状态列表（未配置→空列表）
     */
    public List<UptimeStatusClient.UptimeGroup> execute() {
        return uptimeStatusClient.fetchStatus();
    }
}
