package com.nexa.interfaces.api.ops.dto;

import com.nexa.application.ops.port.UptimeStatusClient;

import java.util.List;

/**
 * Uptime 状态视图（接口层出参 DTO，F-4026 GET /api/uptime/status）。
 *
 * <p>对齐 API-ENDPOINTS §9.4 出参 {@code [{ group, monitors:[{ name, uptime, status }] }]}。
 * 容错语义（空组/单组失败空 monitors）由端口实现承载，视图只裁剪。</p>
 *
 * @param group    监控组名
 * @param monitors 监控项
 */
public record UptimeGroupVO(String group, List<MonitorView> monitors) {

    /**
     * 由端口监控组裁剪为视图。
     *
     * @param group 端口监控组
     * @return 视图
     */
    public static UptimeGroupVO from(UptimeStatusClient.UptimeGroup group) {
        List<MonitorView> monitors = group.monitors().stream().map(MonitorView::from).toList();
        return new UptimeGroupVO(group.group(), monitors);
    }

    /**
     * 单监控项视图。
     *
     * @param name   监控名
     * @param uptime 可用率
     * @param status 状态
     */
    public record MonitorView(String name, double uptime, String status) {

        /**
         * 由端口监控项裁剪。
         *
         * @param m 端口监控项
         * @return 视图
         */
        public static MonitorView from(UptimeStatusClient.Monitor m) {
            return new MonitorView(m.name(), m.uptime(), m.status());
        }
    }
}
