package com.nexa.infrastructure.ops.monitoring;

import com.nexa.application.ops.port.UptimeStatusClient;
import com.nexa.infrastructure.ops.config.OpsProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link UptimeStatusClient} 的 HTTP 实现（基础设施层，F-4026 公开状态接入）。
 *
 * <p>对每个配置的 Uptime-Kuma 监控组 slug，拉取其状态页聚合可用率/状态。容错策略（对齐
 * API-ENDPOINTS §9.4）：未配置 baseUrl/slugs→返回空列表；单组拉取失败→该组返回空 monitors，
 * 不让整体失败（一个状态页挂了不影响其他组展示）。应用层经端口依赖本实现（DDD §2.3）。</p>
 *
 * <p>现网用 errgroup 并发拉取；本实现按 slug 串行拉取（监控组数通常很少，串行简单可靠，
 * 单组失败已隔离），如需并发可在端口不变前提下替换实现。</p>
 */
@Component
public class HttpUptimeStatusClient implements UptimeStatusClient {

    private final OpsProperties opsProperties;
    private final RestClient restClient;

    /**
     * @param opsProperties 运维配置（Uptime-Kuma baseUrl + slugs）
     */
    public HttpUptimeStatusClient(OpsProperties opsProperties) {
        this.opsProperties = opsProperties;
        this.restClient = RestClient.create();
    }

    /** {@inheritDoc} */
    @Override
    public List<UptimeGroup> fetchStatus() {
        OpsProperties.Uptime cfg = opsProperties.getUptime();
        String baseUrl = cfg.getBaseUrl();
        List<String> slugs = cfg.getSlugs();
        // 未接入或未配置监控组 → 空数组（F-4026 容错）。
        if (baseUrl == null || baseUrl.isBlank() || slugs == null || slugs.isEmpty()) {
            return List.of();
        }
        List<UptimeGroup> groups = new ArrayList<>();
        for (String slug : slugs) {
            groups.add(fetchOneGroup(baseUrl, slug));
        }
        return groups;
    }

    /**
     * 拉取单个监控组状态（失败时返回该组空 monitors，不抛出，隔离故障）。
     *
     * @param baseUrl Uptime-Kuma 基址
     * @param slug    状态页 slug
     * @return 该组状态（失败时 monitors 为空）
     */
    @SuppressWarnings("unchecked")
    private UptimeGroup fetchOneGroup(String baseUrl, String slug) {
        try {
            // 拉状态页配置（含监控项名）+ 心跳（含可用率/状态）。Uptime-Kuma 状态页 API：
            // /api/status-page/{slug} 返回监控列表；这里宽松解析，字段缺失即降级。
            Map<String, Object> statusPage = restClient.get()
                    .uri(baseUrl + "/api/status-page/" + slug)
                    .retrieve()
                    .body(Map.class);
            List<Monitor> monitors = parseMonitors(statusPage);
            return new UptimeGroup(slug, monitors);
        } catch (RuntimeException e) {
            // 单组失败容错：返回空 monitors，整体仍成功（避免一个挂掉的状态页拖垮公开状态页）。
            return new UptimeGroup(slug, List.of());
        }
    }

    /**
     * 从状态页响应宽松解析监控项（字段缺失降级为默认值，不抛错）。
     *
     * @param statusPage 状态页响应（可空）
     * @return 监控项列表
     */
    @SuppressWarnings("unchecked")
    private List<Monitor> parseMonitors(Map<String, Object> statusPage) {
        List<Monitor> result = new ArrayList<>();
        if (statusPage == null) {
            return result;
        }
        Object publicGroupList = statusPage.get("publicGroupList");
        if (!(publicGroupList instanceof List<?> groupList)) {
            return result;
        }
        for (Object groupObj : groupList) {
            if (!(groupObj instanceof Map<?, ?> group)) {
                continue;
            }
            Object monitorList = group.get("monitorList");
            if (!(monitorList instanceof List<?> mList)) {
                continue;
            }
            for (Object mObj : mList) {
                if (!(mObj instanceof Map<?, ?> m)) {
                    continue;
                }
                String name = stringOrEmpty(m.get("name"));
                double uptime = doubleOrZero(m.get("uptime"));
                String status = stringOrEmpty(m.get("status"));
                result.add(new Monitor(name, uptime, status));
            }
        }
        return result;
    }

    private String stringOrEmpty(Object o) {
        return o == null ? "" : o.toString();
    }

    private double doubleOrZero(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }
}
