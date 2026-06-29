package com.nexa.deployment.interfaces.api.dto;

import com.nexa.deployment.domain.model.ContainerEvent;
import com.nexa.deployment.domain.model.ContainerList;
import com.nexa.deployment.domain.model.ContainerSummary;

import java.util.List;

/**
 * 部署容器列表出参视图（管理端视图，F-3054）。
 *
 * <p>对齐契约出参 {@code { containers:[{ container_id, device_id, uptime_percent, public_url,
 * events:[{ time, message }] }], total }}（API-ENDPOINTS §10.4）。total 取自领域聚合
 * {@link ContainerList#total()}（无容器→空数组 + total=0）。</p>
 *
 * @param containers 容器视图列表
 * @param total      容器总数
 */
public record ContainerListView(List<ContainerItem> containers, int total) {

    /**
     * 单个容器视图项。
     *
     * @param containerId   容器 ID
     * @param deviceId      设备 ID
     * @param uptimePercent 在线率
     * @param publicUrl     公开 URL
     * @param events        事件流
     */
    public record ContainerItem(
            String containerId,
            String deviceId,
            Double uptimePercent,
            String publicUrl,
            List<EventItem> events) {
    }

    /**
     * 容器事件视图项。
     *
     * @param time    事件时间
     * @param message 事件描述
     */
    public record EventItem(String time, String message) {
    }

    /**
     * 从领域聚合投影为出参视图。
     *
     * @param list 容器列表聚合
     * @return 出参视图
     */
    public static ContainerListView from(ContainerList list) {
        List<ContainerItem> items = list.containers().stream()
                .map(ContainerListView::toItem)
                .toList();
        return new ContainerListView(items, list.total());
    }

    private static ContainerItem toItem(ContainerSummary c) {
        List<EventItem> events = c.events().stream()
                .map(ContainerListView::toEvent)
                .toList();
        return new ContainerItem(c.containerId(), c.deviceId(), c.uptimePercent(), c.publicUrl(), events);
    }

    private static EventItem toEvent(ContainerEvent e) {
        return new EventItem(e.time(), e.message());
    }
}
