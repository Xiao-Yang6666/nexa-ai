package com.nexa.channel.infrastructure.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.channel.application.port.ChannelProbeClient;
import com.nexa.channel.application.port.CodexUsageProbe;
import com.nexa.channel.domain.exception.ChannelUpstreamException;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.model.ChannelTestResult;
import com.nexa.channel.domain.vo.CodexKeyCredential;
import com.nexa.channel.domain.vo.CodexUsage;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 渠道上游网关端口的占位实现（基础设施层 stub adapter，F-2017/F-2018/F-2026/F-2027）。
 *
 * <p><b>切片边界说明（诚实标注）</b>：本片（W2 渠道管理）聚焦渠道 CRUD/搜索/批量/按 tag 启停的
 * 完整领域与持久化落地。真实上游交互（向各上游 provider 发 chat 探测、查计费余额、列模型、
 * 调 Ollama API）涉及大量 provider 适配与网络 IO，按「功能切小、优先保证编译通过」原则，本类先以
 * 行为合理的占位实现承载端口契约：</p>
 * <ul>
 *   <li>{@link #test}：返回成功占位结果（耗时占位 0），不发真实请求——真实现替换为按渠道 type 选适配器探测。</li>
 *   <li>{@link #queryBalance}：返回 0（占位）——真实现按 type 调上游计费 API。</li>
 *   <li>{@link #fetchUpstreamModels}：回显渠道已声明 models 拆分（占位）——真实现调上游 /v1/models。</li>
 *   <li>{@link #ollamaPull}/{@link #ollamaDelete}：空操作（占位）——真实现调 Ollama /api/pull|/api/delete。</li>
 *   <li>{@link #ollamaVersion}：返回占位版本——真实现调 Ollama /api/version。</li>
 * </ul>
 *
 * <p>真实接入时仅替换本 adapter，应用层/领域层（依赖 {@link ChannelProbeClient} 端口）无需改动
 * （DDD 防腐层价值，backend-engineer §2.3）。所有真实失败路径应抛
 * {@code ChannelUpstreamException}（端口契约已声明，接口层映射 502）。</p>
 */
@Component
public class StubChannelProbeClient implements ChannelProbeClient {

    /** type→默认 BaseURL 回落（baseUrl 留空时用）。仅收敛少数常见类型，其余要求显式填 BaseURL。 */
    private static final java.util.Map<Integer, String> DEFAULT_BASE_URL = java.util.Map.of(
            1, "https://api.openai.com",      // OpenAI
            14, "https://api.anthropic.com",  // Anthropic
            54, "https://api.openai.com"      // OpenAI Codex
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public StubChannelProbeClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    /** {@inheritDoc} */
    @Override
    public ChannelTestResult test(Channel channel, String model) {
        // 占位：不发真实请求，返回成功 + 0 耗时。真实现按 channel.type() 选适配器发 chat 探测并计时。
        return ChannelTestResult.success(0.0);
    }

    /** {@inheritDoc} */
    @Override
    public BigDecimal queryBalance(Channel channel) {
        // 占位：返回 0。真实现按 type 调上游计费查询接口（OpenAI/Azure/自定义等各异）。
        return BigDecimal.ZERO;
    }

    /**
     * 探测单渠道上游支持的模型集（F-2026 fetch）：真实调用 OpenAI 兼容的 {@code GET {baseUrl}/v1/models}。
     *
     * <p>取渠道首个 key 作 Bearer 鉴权；baseUrl 留空时按 type 回落官方默认。解析响应 {@code data[].id}
     * 为模型名列表（保序去重）。Ollama 等非 OpenAI 兼容类型暂回落已声明 models（真实现另接各自端点）。
     * 上游不可达 / 非 2xx / 解析失败 → {@link ChannelUpstreamException}（接口层映射 502）。</p>
     */
    @Override
    public List<String> fetchUpstreamModels(Channel channel) {
        // Ollama 等非 OpenAI 兼容协议：暂回落已声明 models（真实现接 {baseUrl}/api/tags）。
        if (channel.type().isOllama()) {
            return splitModels(channel.models());
        }
        String baseUrl = resolveBaseUrl(channel);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ChannelUpstreamException("缺少 BaseURL，无法探测上游模型列表");
        }
        String url = baseUrl.replaceAll("/+$", "") + "/v1/models";
        String apiKey = firstKey(channel.key());
        try {
            String body = restClient.get()
                    .uri(url)
                    .headers(h -> {
                        if (apiKey != null && !apiKey.isBlank()) {
                            h.setBearerAuth(apiKey);
                        }
                    })
                    .retrieve()
                    .body(String.class);
            return parseModelIds(body);
        } catch (ChannelUpstreamException e) {
            throw e;
        } catch (Exception e) {
            // 连接失败 / 非 2xx / 超时：归一为上游故障（不回显 key 等敏感信息）。
            throw new ChannelUpstreamException("探测上游模型列表失败：" + rootMessage(e));
        }
    }

    /** baseUrl 留空时按 type 回落官方默认（无默认则返回原值，由调用方校验）。 */
    private String resolveBaseUrl(Channel channel) {
        String b = channel.baseUrl();
        if (b != null && !b.isBlank()) {
            return b;
        }
        return DEFAULT_BASE_URL.get(channel.type().code());
    }

    /** 多 Key（换行/逗号分隔）取首个非空。 */
    private String firstKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        for (String part : key.split("[\\n,]")) {
            String k = part.trim();
            if (!k.isEmpty()) {
                return k;
            }
        }
        return null;
    }

    /** 解析 OpenAI /v1/models 响应 data[].id（保序去重）。 */
    private List<String> parseModelIds(String body) {
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
            throw new ChannelUpstreamException("上游模型列表响应解析失败");
        }
    }

    private static List<String> splitModels(String models) {
        if (models == null || models.isBlank()) {
            return List.of();
        }
        return Arrays.stream(models.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String m = t.getMessage();
        return m == null ? t.getClass().getSimpleName() : m;
    }

    /** {@inheritDoc} */
    @Override
    public void ollamaPull(Channel channel, String model) {
        // 占位：空操作。真实现 POST {baseUrl}/api/pull {name: model}。
    }

    /** {@inheritDoc} */
    @Override
    public void ollamaDelete(Channel channel, String model) {
        // 占位：空操作。真实现 DELETE {baseUrl}/api/delete {name: model}。
    }

    /** {@inheritDoc} */
    @Override
    public String ollamaVersion(Channel channel) {
        // 占位：返回固定版本。真实现 GET {baseUrl}/api/version 取 version 字段。
        return "unknown";
    }

    /** {@inheritDoc} */
    @Override
    public CodexUsageProbe queryCodexUsage(Channel channel) {
        // 占位：解析凭证（触发 access_token/account_id 缺失校验 → 400），返回空用量、不刷新。
        // 真实现：用 cred.accessToken()/cred.accountId() 调上游 wham 用量接口；上游 401/403 且
        // cred.canRefresh() 时 RefreshCodexOAuthToken 重试，刷新成功经 withRefreshedKey 回传新 key。
        CodexKeyCredential cred = channel.codexCredential();
        // 引用 cred 以表达「凭证已解析校验通过」这一前置（真实现据此发上游请求）。
        assert cred.accessToken() != null;
        return CodexUsageProbe.ofUsage(CodexUsage.of("{}"));
    }
}
