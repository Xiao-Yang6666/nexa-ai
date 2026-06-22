package com.nexa.deployment.interfaces.api.dto;

import com.nexa.deployment.domain.model.IntegrationStatus;

/**
 * io.net 集成设置查询出参视图（管理端视图，F-3039）。
 *
 * <p>对齐契约出参 {@code { provider:"io.net", enabled, configured, can_connect }}（API-ENDPOINTS §10.1）。
 * {@code canConnect} 取自领域聚合 {@link IntegrationStatus#canConnect()}（接口层不重算）。
 * <b>不</b>返回任何 api_key/base_url 等凭证（产品铁律：密钥不下发）。</p>
 *
 * @param provider   集成 provider（固定 io.net）
 * @param enabled    集成是否启用
 * @param configured 是否已配置 api_key
 * @param canConnect 是否可连接（enabled &amp;&amp; configured，领域派生）
 */
public record IntegrationStatusView(
        String provider,
        boolean enabled,
        boolean configured,
        boolean canConnect) {

    /**
     * 从领域聚合投影为出参视图。
     *
     * @param status 集成状态聚合
     * @return 出参视图
     */
    public static IntegrationStatusView from(IntegrationStatus status) {
        return new IntegrationStatusView(
                status.provider(),
                status.enabled(),
                status.configured(),
                status.canConnect());
    }
}
