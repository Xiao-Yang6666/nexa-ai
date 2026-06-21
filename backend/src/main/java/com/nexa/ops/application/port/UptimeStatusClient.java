package com.nexa.ops.application.port;

import java.util.List;

/**
 * Uptime-Kuma 状态接入端口（应用层端口，F-4026 GET /api/uptime/status 公开）。
 *
 * <p>抽象对外部 Uptime-Kuma 状态页的聚合拉取（各监控组的可用率/心跳状态），由基础设施层用
 * HTTP 客户端并发实现（errgroup 类比；httpTimeout 10s / requestTimeout 30s）。未配置 groups→空列表；
 * 单组拉取失败→该组空 monitors（容错，不整体失败，API-ENDPOINTS §9.4）。</p>
 */
public interface UptimeStatusClient {

    /**
     * 拉取各监控组状态（容错聚合）。
     *
     * @return 监控组列表（未配置→空列表）
     */
    List<UptimeGroup> fetchStatus();

    /**
     * 监控组（只读，对齐出参 {@code { group, monitors }}）。
     *
     * @param group    分组名
     * @param monitors 该组监控项
     */
    record UptimeGroup(String group, List<Monitor> monitors) {
    }

    /**
     * 单个监控项（只读，对齐出参 {@code { name, uptime, status }}）。
     *
     * @param name   监控名
     * @param uptime 可用率（0~1 或百分比，按上游原值透传）
     * @param status 状态（如 up/down/pending）
     */
    record Monitor(String name, double uptime, String status) {
    }
}
