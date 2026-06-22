package com.nexa.deployment.infrastructure.ionet;

import com.nexa.deployment.application.port.IonetClient;
import com.nexa.deployment.domain.exception.DeploymentNameUnavailableException;
import com.nexa.deployment.domain.exception.IonetIntegrationException;
import com.nexa.deployment.domain.model.ConnectionTestResult;
import com.nexa.deployment.domain.model.ContainerEvent;
import com.nexa.deployment.domain.model.ContainerList;
import com.nexa.deployment.domain.model.ContainerSummary;
import com.nexa.deployment.domain.model.DeploymentList;
import com.nexa.deployment.domain.model.DeploymentSummary;
import com.nexa.deployment.domain.model.HardwareCatalog;
import com.nexa.deployment.domain.model.HardwareType;
import com.nexa.deployment.domain.model.IntegrationStatus;
import com.nexa.deployment.domain.model.Location;
import com.nexa.deployment.domain.model.LocationCatalog;
import com.nexa.deployment.domain.model.NameAvailability;
import com.nexa.deployment.domain.vo.ClusterName;
import com.nexa.deployment.domain.vo.ContainerId;
import com.nexa.deployment.domain.vo.DeploymentId;
import com.nexa.deployment.domain.vo.DeploymentName;
import com.nexa.deployment.domain.vo.GpuCount;
import com.nexa.deployment.domain.vo.HardwareId;
import com.nexa.deployment.domain.vo.LogQuery;
import com.nexa.deployment.domain.vo.Pagination;
import com.nexa.deployment.infrastructure.config.IonetSettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * io.net 企业部署上游网关端口 {@link IonetClient} 的实现（基础设施层防腐适配器，F-3049~F-3056 后半）。
 *
 * <p>封装与 io.net 企业 API 的全部交互：端点 URL、Bearer api_key 鉴权、响应字段映射到领域模型、
 * 敏感键剔除、错误归一。应用/领域层只依赖端口接口，本类是唯一接触 HTTP/io.net 协议细节处
 * （backend-engineer §2.3 + 防腐层）。</p>
 *
 * <p><b>未配置即明确失败（不静默）</b>：base_url/api_key 缺省为空。需 EnterpriseClient（鉴权）的接口在
 * 缺 api_key 时抛 {@link IonetIntegrationException}（接口层 502），不发无效请求。这样单 provider 未配置
 * 不影响启动与编译（对齐 com.nexa.account OAuth client 的「配置缺省不在启动期报错」约定）。</p>
 *
 * <p><b>敏感键剔除（产品铁律）</b>：所有原始透传响应（副本/价格/容器详情/日志）经
 * {@link #stripSensitive} 移除 api_key 等密钥键再下发（API-ENDPOINTS §10 铁律「api_key 等密钥经敏感键
 * 剔除规则不下发列表」），杜绝凭证经客户端视图泄露。</p>
 *
 * <p><b>错误处理</b>：网络/解析/上游 APIError 一律 wrap 成 {@link IonetIntegrationException} 带上下文
 * 向上抛（不吞错，backend-engineer §3.2），message 透传上游 {@code APIError.Message}（空时回退稳定文案）。</p>
 */
@Component
public class IonetClientImpl implements IonetClient {

    /** 敏感键名（小写匹配），命中即从下发响应中剔除（产品铁律：密钥不下发）。 */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "api_key", "apikey", "api-key", "secret", "token", "access_token",
            "client_secret", "password", "authorization");

    private final IonetSettings settings;
    private final RestClient restClient;

    /**
     * @param settings io.net 集成设置（base_url/api_key/enabled）
     */
    public IonetClientImpl(IonetSettings settings) {
        this.settings = settings;
        this.restClient = RestClient.create();
    }

    /** {@inheritDoc} */
    @Override
    public HardwareCatalog listHardwareTypes() {
        // EnterpriseClient：需 api_key。
        Map<String, Object> resp = getEnterprise("/v1/hardware-types", null);
        return toHardwareCatalog(resp);
    }

    /**
     * 将硬件类型上游响应映射为领域聚合（{@link #listHardwareTypes} 与 {@link #testConnection} 共用）。
     *
     * @param resp 上游响应（含 data.hardware_types）
     * @return 硬件类型聚合
     */
    private HardwareCatalog toHardwareCatalog(Map<String, Object> resp) {
        Map<String, Object> data = asMap(resp.get("data"), resp);
        List<HardwareType> types = new ArrayList<>();
        for (Object item : asList(data.get("hardware_types"))) {
            Map<String, Object> m = asMap(item, Map.of());
            types.add(new HardwareType(
                    asLong(m.get("id"), 0L),
                    asString(m.get("name")),
                    asLong(m.get("available_replicas"), 0L),
                    stripSensitive(m)));
        }
        return new HardwareCatalog(types);
    }

    /** {@inheritDoc} */
    @Override
    public LocationCatalog listLocations() {
        // 普通 Client（locations 契约标注普通 Client）。
        Map<String, Object> resp = getPlain("/v1/locations");
        Map<String, Object> data = asMap(resp.get("data"), resp);
        List<Location> locations = new ArrayList<>();
        for (Object item : asList(data.get("locations"))) {
            Map<String, Object> m = asMap(item, Map.of());
            locations.add(new Location(
                    asString(m.get("id")),
                    asString(m.get("name")),
                    stripSensitive(m)));
        }
        long upstreamTotal = asLong(data.get("total"), 0L);
        return new LocationCatalog(locations, upstreamTotal);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> queryAvailableReplicas(HardwareId hardwareId, GpuCount gpuCount) {
        String path = UriComponentsBuilder.fromPath("/v1/replicas")
                .queryParam("hardware_id", hardwareId.value())
                .queryParam("gpu_count", gpuCount.value())
                .build().toUriString();
        Map<String, Object> resp = getEnterprise(path, null);
        return stripSensitive(unwrapData(resp));
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> estimatePrice(Map<String, Object> request) {
        Map<String, Object> resp = postEnterprise("/v1/price-estimation", request);
        return stripSensitive(unwrapData(resp));
    }

    /** {@inheritDoc} */
    @Override
    public NameAvailability checkClusterName(ClusterName name) {
        String path = UriComponentsBuilder.fromPath("/v1/check-name")
                .queryParam("name", name.value())
                .build().toUriString();
        Map<String, Object> resp = getEnterprise(path, null);
        Map<String, Object> data = unwrapData(resp);
        boolean available = asBool(data.get("available"));
        return new NameAvailability(name.value(), available);
    }

    /** {@inheritDoc} */
    @Override
    public ContainerList listContainers(DeploymentId deploymentId) {
        String path = "/v1/deployments/" + enc(deploymentId.value()) + "/containers";
        Map<String, Object> resp = getEnterprise(path, null);
        Map<String, Object> data = asMap(resp.get("data"), resp);
        List<ContainerSummary> containers = new ArrayList<>();
        for (Object item : asList(data.get("containers"))) {
            Map<String, Object> m = asMap(item, Map.of());
            List<ContainerEvent> events = new ArrayList<>();
            for (Object ev : asList(m.get("events"))) {
                Map<String, Object> em = asMap(ev, Map.of());
                events.add(new ContainerEvent(asString(em.get("time")), asString(em.get("message"))));
            }
            containers.add(new ContainerSummary(
                    asString(m.get("container_id")),
                    asString(m.get("device_id")),
                    asDoubleOrNull(m.get("uptime_percent")),
                    asString(m.get("public_url")),
                    events));
        }
        return new ContainerList(containers);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getContainerDetail(DeploymentId deploymentId, ContainerId containerId) {
        String path = "/v1/deployments/" + enc(deploymentId.value())
                + "/containers/" + enc(containerId.value());
        Map<String, Object> resp = getEnterprise(path, null);
        Map<String, Object> data = unwrapData(resp);
        // 契约 F-3055：details 为空→「container details not found」。
        if (data.isEmpty()) {
            throw new IonetIntegrationException("container details not found");
        }
        return stripSensitive(data);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getContainerLogs(DeploymentId deploymentId, ContainerId containerId, LogQuery query) {
        // 普通 Client（logs 契约标注普通 Client）。
        UriComponentsBuilder b = UriComponentsBuilder
                .fromPath("/v1/deployments/" + enc(deploymentId.value())
                        + "/containers/" + enc(containerId.value()) + "/logs")
                .queryParam("container_id", containerId.value())
                .queryParam("limit", query.limit())
                .queryParam("follow", query.follow());
        if (query.level() != null) {
            b.queryParam("level", query.level());
        }
        if (query.stream() != null) {
            b.queryParam("stream", query.stream());
        }
        if (query.cursor() != null) {
            b.queryParam("cursor", query.cursor());
        }
        if (query.startTime() != null) {
            b.queryParam("start_time", query.startTime());
        }
        if (query.endTime() != null) {
            b.queryParam("end_time", query.endTime());
        }
        Map<String, Object> resp = getPlain(b.build().toUriString());
        return stripSensitive(unwrapData(resp));
    }

    // ---------------------------------------------------------------------
    // 集成开关与连接（F-3039/F-3040）
    // ---------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public IntegrationStatus getIntegrationStatus() {
        // 纯本地配置派生，不发上游请求（契约 F-3039）。
        return IntegrationStatus.ionet(settings.isEnabled(), settings.isConfigured());
    }

    /** {@inheritDoc} */
    @Override
    public ConnectionTestResult testConnection(String apiKeyOverride) {
        // 连接测试 = 用待验证 key 拉硬件目录（key 有效才能拉成功），再投影为 hardware_count/total_available。
        // override 为空则回退 stored key；两者皆空抛「api_key is required」（契约 F-3040）。
        String key = (apiKeyOverride == null || apiKeyOverride.isBlank())
                ? settings.getApiKey() : apiKeyOverride.trim();
        if (key == null || key.isBlank()) {
            throw new IonetIntegrationException("api_key is required");
        }
        requireBaseUrl();
        Map<String, Object> resp = exchange(() -> restClient.get()
                .uri(settings.getBaseUrl() + "/v1/hardware-types")
                .header("Authorization", "Bearer " + key)
                .header("Accept", "application/json")
                .retrieve()
                .body(Map.class));
        return ConnectionTestResult.from(toHardwareCatalog(resp));
    }

    // ---------------------------------------------------------------------
    // 部署 CRUD 与运维（F-3041~F-3048）
    // ---------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public DeploymentList listDeployments(Pagination pagination) {
        String path = UriComponentsBuilder.fromPath("/v1/deployments")
                .queryParam("p", pagination.page())
                .queryParam("page_size", pagination.pageSize())
                .build().toUriString();
        Map<String, Object> resp = getEnterprise(path, null);
        return toDeploymentList(resp, pagination);
    }

    /** {@inheritDoc} */
    @Override
    public DeploymentList searchDeployments(String status, Pagination pagination) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/v1/deployments")
                .queryParam("p", pagination.page())
                .queryParam("page_size", pagination.pageSize());
        if (status != null && !status.isBlank()) {
            // status 透传上游过滤（契约 F-3042：status 作为上游过滤参数）。
            b.queryParam("status", status.trim());
        }
        Map<String, Object> resp = getEnterprise(b.build().toUriString(), null);
        return toDeploymentList(resp, pagination);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getDeploymentDetail(DeploymentId deploymentId) {
        String path = "/v1/deployments/" + enc(deploymentId.value());
        Map<String, Object> resp = getEnterprise(path, null);
        return stripSensitive(unwrapData(resp));
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> createDeployment(Map<String, Object> request) {
        Map<String, Object> resp = postEnterprise("/v1/deployments", request);
        return stripSensitive(unwrapData(resp));
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> updateDeployment(DeploymentId deploymentId, Map<String, Object> request) {
        String path = "/v1/deployments/" + enc(deploymentId.value());
        Map<String, Object> resp = putEnterprise(path, request);
        return stripSensitive(unwrapData(resp));
    }

    /** {@inheritDoc} */
    @Override
    public void renameDeployment(DeploymentId deploymentId, DeploymentName name) {
        // 契约 F-3046：预检名称可用性 → 不可用抛 409（name is not available）；
        // 预检本身失败（上游故障）抛 502（failed to check name availability）。
        NameAvailability availability;
        try {
            availability = checkClusterName(new ClusterName(name.value()));
        } catch (IonetIntegrationException e) {
            // 预检调用失败：归一为契约固定文案（不泄露底层细节）。
            throw new IonetIntegrationException("failed to check name availability");
        }
        if (!availability.available()) {
            throw new DeploymentNameUnavailableException();
        }
        // 预检通过 → 提交重命名（覆盖式，幂等键 deployment_id+name）。
        String path = "/v1/deployments/" + enc(deploymentId.value()) + "/name";
        putEnterprise(path, Map.of("name", name.value()));
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> extendDeployment(DeploymentId deploymentId, Map<String, Object> request) {
        String path = "/v1/deployments/" + enc(deploymentId.value()) + "/extend";
        Map<String, Object> resp = postEnterprise(path, request);
        return stripSensitive(unwrapData(resp));
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> deleteDeployment(DeploymentId deploymentId) {
        String path = "/v1/deployments/" + enc(deploymentId.value());
        Map<String, Object> resp = deleteEnterprise(path);
        return stripSensitive(unwrapData(resp));
    }

    // ---------------------------------------------------------------------
    // 上游调用（鉴权 + 错误归一）
    // ---------------------------------------------------------------------

    /**
     * EnterpriseClient GET：要求已配置 api_key，附 Bearer 头调用上游。
     *
     * @param path 端点路径（含 query）
     * @param unused 占位（保持与 POST 对称的调用形态）
     * @return 上游响应映射
     * @throws IonetIntegrationException 未配置 / 网络 / 解析失败
     */
    private Map<String, Object> getEnterprise(String path, Object unused) {
        requireApiKey();
        return exchange(() -> restClient.get()
                .uri(settings.getBaseUrl() + path)
                .header("Authorization", "Bearer " + settings.getApiKey())
                .header("Accept", "application/json")
                .retrieve()
                .body(Map.class));
    }

    /**
     * EnterpriseClient POST：要求已配置 api_key，附 Bearer 头提交 JSON body。
     *
     * @param path 端点路径
     * @param body 请求体
     * @return 上游响应映射
     * @throws IonetIntegrationException 未配置 / 网络 / 解析失败
     */
    private Map<String, Object> postEnterprise(String path, Map<String, Object> body) {
        requireApiKey();
        return exchange(() -> restClient.post()
                .uri(settings.getBaseUrl() + path)
                .header("Authorization", "Bearer " + settings.getApiKey())
                .header("Accept", "application/json")
                .body(body == null ? Map.of() : body)
                .retrieve()
                .body(Map.class));
    }

    /**
     * EnterpriseClient PUT：要求已配置 api_key，附 Bearer 头提交 JSON body（覆盖式更新/重命名）。
     *
     * @param path 端点路径
     * @param body 请求体
     * @return 上游响应映射
     * @throws IonetIntegrationException 未配置 / 网络 / 解析失败
     */
    private Map<String, Object> putEnterprise(String path, Map<String, Object> body) {
        requireApiKey();
        return exchange(() -> restClient.put()
                .uri(settings.getBaseUrl() + path)
                .header("Authorization", "Bearer " + settings.getApiKey())
                .header("Accept", "application/json")
                .body(body == null ? Map.of() : body)
                .retrieve()
                .body(Map.class));
    }

    /**
     * EnterpriseClient DELETE：要求已配置 api_key，附 Bearer 头请求终止。
     *
     * @param path 端点路径
     * @return 上游响应映射（保证非 null，由 {@link #exchange} 兜底）
     * @throws IonetIntegrationException 未配置 / 网络 / 解析失败
     */
    private Map<String, Object> deleteEnterprise(String path) {
        requireApiKey();
        return exchange(() -> restClient.method(org.springframework.http.HttpMethod.DELETE)
                .uri(settings.getBaseUrl() + path)
                .header("Authorization", "Bearer " + settings.getApiKey())
                .header("Accept", "application/json")
                .retrieve()
                .body(Map.class));
    }

    /**
     * 普通 Client GET：不强制 api_key（locations/logs），有 key 仍附带便于上游识别。
     *
     * @param path 端点路径（含 query）
     * @return 上游响应映射
     * @throws IonetIntegrationException 未配置 base_url / 网络 / 解析失败
     */
    private Map<String, Object> getPlain(String path) {
        requireBaseUrl();
        return exchange(() -> {
            var spec = restClient.get()
                    .uri(settings.getBaseUrl() + path)
                    .header("Accept", "application/json");
            if (settings.isConfigured()) {
                spec = spec.header("Authorization", "Bearer " + settings.getApiKey());
            }
            return spec.retrieve().body(Map.class);
        });
    }

    /**
     * 统一执行上游调用并归一错误：捕获一切 RuntimeException wrap 成集成异常（不吞错）。
     *
     * @param call 实际上游调用
     * @return 上游响应映射（保证非 null）
     * @throws IonetIntegrationException 上游失败 / APIError / 空响应
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> exchange(UpstreamCall call) {
        Map<String, Object> resp;
        try {
            resp = call.execute();
        } catch (RuntimeException e) {
            // 网络/HTTP/反序列化失败：wrap 带上游上下文向上抛，由 GlobalExceptionHandler 翻译 502。
            throw new IonetIntegrationException("io.net request failed: " + e.getMessage());
        }
        if (resp == null) {
            throw new IonetIntegrationException("failed to validate api key");
        }
        // 上游 APIError 内嵌错误：success=false 或带 error 字段时透传其 message（空回退稳定文案）。
        if (Boolean.FALSE.equals(resp.get("success")) || resp.get("error") != null) {
            String msg = extractUpstreamError(resp);
            throw new IonetIntegrationException(msg == null || msg.isBlank()
                    ? "failed to validate api key" : msg);
        }
        return resp;
    }

    /**
     * 校验已配置 api_key（EnterpriseClient 前置）。
     *
     * @throws IonetIntegrationException 未配置 base_url 或 api_key
     */
    private void requireApiKey() {
        requireBaseUrl();
        if (!settings.isConfigured()) {
            throw new IonetIntegrationException("api_key is required");
        }
    }

    /**
     * 校验已配置 base_url（任何上游调用前置）。
     *
     * @throws IonetIntegrationException 未配置 base_url
     */
    private void requireBaseUrl() {
        if (settings.getBaseUrl() == null || settings.getBaseUrl().isBlank()) {
            throw new IonetIntegrationException("io.net base url is not configured");
        }
    }

    // ---------------------------------------------------------------------
    // 防御式响应解析 + 敏感键剔除（纯函数，无副作用）
    // ---------------------------------------------------------------------

    /**
     * 提取上游错误 message：优先 {@code error.message}，回退顶层 {@code message}。
     *
     * @param resp 上游响应
     * @return 错误 message（可空）
     */
    private static String extractUpstreamError(Map<String, Object> resp) {
        Object error = resp.get("error");
        if (error instanceof Map<?, ?> em) {
            Object m = em.get("message");
            if (m != null) {
                return String.valueOf(m);
            }
        }
        Object topMsg = resp.get("message");
        return topMsg == null ? null : String.valueOf(topMsg);
    }

    /**
     * 取响应的 {@code data} 段（无 data 时回退整个响应去掉信封）。
     *
     * @param resp 上游响应
     * @return data 映射（保证非 null）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapData(Map<String, Object> resp) {
        Object data = resp.get("data");
        if (data instanceof Map<?, ?>) {
            return (Map<String, Object>) data;
        }
        return resp;
    }

    /**
     * 将部署列表上游响应映射为领域聚合（F-3041/F-3042 共用）。
     *
     * <p>从 {@code data.items}（或回退顶层 {@code items}）抽取部署概要，每行只保留契约 6 字段
     * （deployment_id/name/status/time_remaining/hardware_info/compute_minutes_remaining），上游 total 透传
     * （领域层据 items 派生 status_counts，搜索时再据关键词修正 total）。</p>
     *
     * @param resp       上游响应
     * @param pagination 归一后的分页参数（透传 page/page_size）
     * @return 部署列表聚合
     */
    private DeploymentList toDeploymentList(Map<String, Object> resp, Pagination pagination) {
        Map<String, Object> data = asMap(resp.get("data"), resp);
        // items 可能在 data.items 或顶层 items（防御两种上游信封）。
        Object itemsRaw = data.get("items") != null ? data.get("items") : resp.get("items");
        List<DeploymentSummary> items = new ArrayList<>();
        for (Object item : asList(itemsRaw)) {
            Map<String, Object> m = asMap(item, Map.of());
            Long computeMinutes = asLongOrNull(m.get("compute_minutes_remaining"));
            items.add(new DeploymentSummary(
                    asString(m.get("deployment_id")),
                    asString(m.get("name")),
                    asString(m.get("status")),
                    asString(m.get("time_remaining")),
                    asString(m.get("hardware_info")),
                    computeMinutes));
        }
        // 上游 total（缺省回退 items 长度）。
        long upstreamTotal = asLong(data.get("total"), items.size());
        return DeploymentList.ofListing(items, upstreamTotal, pagination);
    }

    /**
     * 深度剔除敏感键，返回新的不可变安全映射（产品铁律：密钥不下发客户端）。
     *
     * <p>递归处理嵌套 Map / List，命中 {@link #SENSITIVE_KEYS}（小写匹配）的键整条丢弃。
     * 不修改入参（纯函数），保证幂等可测。</p>
     *
     * @param source 原始上游映射
     * @return 剔除敏感键后的安全映射
     */
    static Map<String, Object> stripSensitive(Map<String, Object> source) {
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : source.entrySet()) {
            String key = e.getKey();
            if (key != null && SENSITIVE_KEYS.contains(key.toLowerCase())) {
                continue; // 命中敏感键：整条剔除，不下发。
            }
            safe.put(key, sanitizeValue(e.getValue()));
        }
        return safe;
    }

    /**
     * 递归净化值：Map 走 stripSensitive，List 逐项净化，标量原样。
     *
     * @param value 原始值
     * @return 净化后的值
     */
    @SuppressWarnings("unchecked")
    private static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> m) {
            return stripSensitive((Map<String, Object>) m);
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(sanitizeValue(item));
            }
            return out;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o, Map<String, Object> fallback) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : fallback;
    }

    private static List<?> asList(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static long asLong(Object o, long fallback) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Long asLongOrNull(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Double asDoubleOrNull(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean asBool(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        return o instanceof String s && Boolean.parseBoolean(s.trim());
    }

    private static String enc(String segment) {
        // path 段编码：避免 deployment/container id 含特殊字符破坏 URL（防注入）。
        return org.springframework.web.util.UriUtils.encodePathSegment(segment, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 上游调用函数式封装（供 {@link #exchange} 统一错误归一）。 */
    @FunctionalInterface
    private interface UpstreamCall {
        @SuppressWarnings("rawtypes")
        Map execute();
    }
}
