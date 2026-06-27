package com.nexa.account.provider.infrastructure.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.account.provider.application.port.ProviderModelProbePort;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 上游模型探测适配器（账号域基础设施层）：真实调上游 {@code /models} 拉模型 ID。
 *
 * <p>按 platform 分三类协议规则：
 * <ul>
 *   <li><b>Anthropic</b>：{@code GET {base}/v1/models}，头 {@code x-api-key} + {@code anthropic-version}；解析 {@code data[].id}。</li>
 *   <li><b>Google (Gemini)</b>：{@code GET {base}/v1beta/models?key=...}；解析 {@code models[].name}（去 {@code models/} 前缀）。</li>
 *   <li><b>OpenAI 兼容（其余全部）</b>：{@code GET {base}/v1/models}，头 {@code Authorization: Bearer}；解析 {@code data[].id}。</li>
 * </ul>
 * baseUrl 留空时按 platform 回落官方默认。apiKey 仅注入请求头/参数，绝不落日志；失败归一为
 * {@link ProviderProbeException}（不回显 key）。</p>
 */
@Component
public class RestClientProviderModelProbeAdapter implements ProviderModelProbePort {

    /** platform → 官方默认 BaseURL（baseUrl 留空时回落）。 */
    private static final Map<String, String> DEFAULT_BASE_URL = Map.of(
            "openai", "https://api.openai.com",
            "anthropic", "https://api.anthropic.com",
            "deepseek", "https://api.deepseek.com",
            "moonshot", "https://api.moonshot.cn",
            "mistral", "https://api.mistral.ai",
            "zhipu", "https://open.bigmodel.cn/api/paas",
            "minimax", "https://api.minimax.chat",
            "google", "https://generativelanguage.googleapis.com"
    );

    /** Anthropic API 版本头（固定值，对齐官方稳定版）。 */
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RestClientProviderModelProbeAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    /** {@inheritDoc} */
    @Override
    public List<String> fetchModels(String platform, String baseUrl, String apiKey) {
        String p = platform == null ? "" : platform.trim().toLowerCase();
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) {
            throw new ProviderProbeException("缺少 API Key，无法获取模型列表");
        }
        String base = resolveBaseUrl(p, baseUrl);
        if (base == null || base.isBlank()) {
            throw new ProviderProbeException("缺少 Base URL，且该平台无默认地址，请先填写 Base URL");
        }
        String root = base.replaceAll("/+$", "");

        if (p.contains("anthropic") || p.contains("claude")) {
            return fetchAnthropic(root, key);
        }
        if (p.contains("google") || p.contains("gemini")) {
            return fetchGemini(root, key);
        }
        return fetchOpenAiCompatible(root, key);
    }

    /** OpenAI 兼容：GET {base}/v1/models，Bearer 鉴权，解析 data[].id。 */
    private List<String> fetchOpenAiCompatible(String root, String key) {
        String url = root + "/v1/models";
        try {
            String body = restClient.get()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(key))
                    .retrieve()
                    .body(String.class);
            return parseDataIds(body);
        } catch (ProviderProbeException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderProbeException("获取模型列表失败：" + rootMessage(e));
        }
    }

    /** Anthropic：GET {base}/v1/models，x-api-key + anthropic-version，解析 data[].id。 */
    private List<String> fetchAnthropic(String root, String key) {
        String url = root + "/v1/models";
        try {
            String body = restClient.get()
                    .uri(url)
                    .headers(h -> {
                        h.set("x-api-key", key);
                        h.set("anthropic-version", ANTHROPIC_VERSION);
                    })
                    .retrieve()
                    .body(String.class);
            return parseDataIds(body);
        } catch (ProviderProbeException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderProbeException("获取模型列表失败：" + rootMessage(e));
        }
    }

    /** Gemini：GET {base}/v1beta/models?key=...，解析 models[].name（去 models/ 前缀）。 */
    private List<String> fetchGemini(String root, String key) {
        String url = root + "/v1beta/models?key=" + key;
        try {
            String body = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);
            return parseGeminiNames(body);
        } catch (ProviderProbeException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderProbeException("获取模型列表失败：" + rootMessage(e));
        }
    }

    private String resolveBaseUrl(String platform, String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl.trim();
        }
        return DEFAULT_BASE_URL.get(platform);
    }

    /** 解析 OpenAI/Anthropic 风格 data[].id（保序去重）。 */
    private List<String> parseDataIds(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode data = objectMapper.readTree(body).path("data");
            Set<String> seen = new LinkedHashSet<>();
            if (data.isArray()) {
                for (JsonNode item : data) {
                    String id = item.path("id").asText("").trim();
                    if (!id.isEmpty()) {
                        seen.add(id);
                    }
                }
            }
            return new ArrayList<>(seen);
        } catch (Exception e) {
            throw new ProviderProbeException("模型列表响应解析失败");
        }
    }

    /** 解析 Gemini models[].name（去 "models/" 前缀，保序去重）。 */
    private List<String> parseGeminiNames(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode models = objectMapper.readTree(body).path("models");
            Set<String> seen = new LinkedHashSet<>();
            if (models.isArray()) {
                for (JsonNode item : models) {
                    String name = item.path("name").asText("").trim();
                    if (name.startsWith("models/")) {
                        name = name.substring("models/".length());
                    }
                    if (!name.isEmpty()) {
                        seen.add(name);
                    }
                }
            }
            return new ArrayList<>(seen);
        } catch (Exception e) {
            throw new ProviderProbeException("模型列表响应解析失败");
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String m = t.getMessage();
        return m == null ? t.getClass().getSimpleName() : m;
    }
}
