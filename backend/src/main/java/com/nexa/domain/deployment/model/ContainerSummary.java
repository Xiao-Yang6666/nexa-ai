package com.nexa.domain.deployment.model;

import java.util.List;

/**
 * 部署容器只读视图（io.net container，F-3054）。
 *
 * <p>领域模型，零框架依赖。承载「部署容器列表」单个容器及其事件流。对齐契约出参字段
 * （API-ENDPOINTS §10.4）：{@code container_id, device_id, uptime_percent, public_url, events[]}。</p>
 *
 * @param containerId   容器 ID
 * @param deviceId      设备 ID（可空）
 * @param uptimePercent 在线率百分比（可空）
 * @param publicUrl     容器公开访问 URL（可空）
 * @param events        容器事件流（时间倒序由上游决定；本视图按上游顺序保留）
 */
public record ContainerSummary(
        String containerId,
        String deviceId,
        Double uptimePercent,
        String publicUrl,
        List<ContainerEvent> events) {

    /**
     * 紧凑构造器：防御式拷贝事件列表为不可变列表（无容器/无事件时归一空列表）。
     */
    public ContainerSummary {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
