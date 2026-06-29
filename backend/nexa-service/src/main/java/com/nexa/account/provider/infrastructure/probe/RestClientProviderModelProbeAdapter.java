package com.nexa.account.provider.infrastructure.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexa.account.provider.application.port.ProviderModelProbePort;
import com.nexa.account.provider.application.port.ProviderModelTestPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
public class RestClientProviderModelProbeAdapter implements ProviderModelProbePort, ProviderModelTestPort {

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

    /** 测试默认提示词（用户未填时回落）。 */
    private static final String DEFAULT_PROMPT = "你好，请回复 OK";

    /** 测试回复片段最大长度（截断，避免回前端过大）。 */
    private static final int MAX_REPLY_LEN = 500;

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

    /* ── 模型连通性测试（ProviderModelTestPort）：发一次非流式 chat 补全 ── */

    /** {@inheritDoc} */
    @Override
    public ProviderModelTestResult testChat(String platform, String baseUrl, String apiKey,
                                            String model, String prompt) {
        String p = platform == null ? "" : platform.trim().toLowerCase();
        String key = apiKey == null ? "" : apiKey.trim();
        String mdl = model == null ? "" : model.trim();
        String text = (prompt == null || prompt.isBlank()) ? DEFAULT_PROMPT : prompt.trim();
        if (key.isEmpty()) {
            throw new ProviderProbeException("缺少 API Key，无法测试模型");
        }
        if (mdl.isEmpty()) {
            throw new ProviderProbeException("缺少要测试的模型");
        }
        String base = resolveBaseUrl(p, baseUrl);
        if (base == null || base.isBlank()) {
            throw new ProviderProbeException("缺少 Base URL，且该平台无默认地址，请先填写 Base URL");
        }
        String root = base.replaceAll("/+$", "");

        if (p.contains("anthropic") || p.contains("claude")) {
            return testAnthropic(root, key, mdl, text);
        }
        if (p.contains("google") || p.contains("gemini")) {
            return testGemini(root, key, mdl, text);
        }
        return testOpenAiCompatible(root, key, mdl, text);
    }

    /** OpenAI 兼容：POST {base}/v1/chat/completions，Bearer，解析 choices[0].message.content。 */
    private ProviderModelTestResult testOpenAiCompatible(String root, String key, String model, String prompt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", prompt);
        String url = root + "/v1/chat/completions";
        long t0 = System.nanoTime();
        String resp = post(url, body, h -> h.setBearerAuth(key));
        long ms = elapsedMs(t0);
        String reply = textAt(resp, "choices", 0, "message", "content");
        return new ProviderModelTestResult(ms, reply);
    }

    /** Anthropic：POST {base}/v1/messages，x-api-key + version，解析 content[0].text。 */
    private ProviderModelTestResult testAnthropic(String root, String key, String model, String prompt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 64);
        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", prompt);
        String url = root + "/v1/messages";
        long t0 = System.nanoTime();
        String resp = post(url, body, h -> {
            h.set("x-api-key", key);
            h.set("anthropic-version", ANTHROPIC_VERSION);
        });
        long ms = elapsedMs(t0);
        String reply = anthropicText(resp);
        return new ProviderModelTestResult(ms, reply);
    }

    /** Gemini：POST {base}/v1beta/models/{model}:generateContent?key=...，解析 candidates[0].content.parts[0].text。 */
    private ProviderModelTestResult testGemini(String root, String key, String model, String prompt) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode contents = body.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", prompt);
        String url = root + "/v1beta/models/" + model + ":generateContent?key=" + key;
        long t0 = System.nanoTime();
        String resp = post(url, body, h -> { });
        long ms = elapsedMs(t0);
        String reply = geminiText(resp);
        return new ProviderModelTestResult(ms, reply);
    }

    /* ── 流式模型测试（ProviderModelTestPort.testChatStream）：stream=true 逐 token 回调 ── */

    /** {@inheritDoc} */
    @Override
    public void testChatStream(String platform, String baseUrl, String apiKey,
                               String model, String prompt, TestStreamListener listener) {
        String p = platform == null ? "" : platform.trim().toLowerCase();
        String key = apiKey == null ? "" : apiKey.trim();
        String mdl = model == null ? "" : model.trim();
        String text = (prompt == null || prompt.isBlank()) ? DEFAULT_PROMPT : prompt.trim();
        if (key.isEmpty()) {
            throw new ProviderProbeException("缺少 API Key，无法测试模型");
        }
        if (mdl.isEmpty()) {
            throw new ProviderProbeException("缺少要测试的模型");
        }
        String base = resolveBaseUrl(p, baseUrl);
        if (base == null || base.isBlank()) {
            throw new ProviderProbeException("缺少 Base URL，且该平台无默认地址，请先填写 Base URL");
        }
        String root = base.replaceAll("/+$", "");

        if (p.contains("anthropic") || p.contains("claude")) {
            streamAnthropic(root, key, mdl, text, listener);
        } else if (p.contains("google") || p.contains("gemini")) {
            streamGemini(root, key, mdl, text, listener);
        } else {
            streamOpenAiCompatible(root, key, mdl, text, listener);
        }
    }

    /** OpenAI 兼容流式：POST /v1/chat/completions stream=true，逐 data: 事件取 choices[0].delta.content。 */
    private void streamOpenAiCompatible(String root, String key, String model, String prompt,
                                        TestStreamListener listener) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", true);
        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", prompt);
        streamSse(root + "/v1/chat/completions", body, h -> h.setBearerAuth(key), listener,
                event -> deltaText(event, "choices", "delta", "content"));
    }

    /** Anthropic 流式：POST /v1/messages stream=true，逐事件取 delta.text（content_block_delta）。 */
    private void streamAnthropic(String root, String key, String model, String prompt,
                                 TestStreamListener listener) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 64);
        body.put("stream", true);
        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", prompt);
        streamSse(root + "/v1/messages", body, h -> {
            h.set("x-api-key", key);
            h.set("anthropic-version", ANTHROPIC_VERSION);
        }, listener, this::anthropicDelta);
    }

    /** Gemini 流式：POST :streamGenerateContent?alt=sse&key=...，逐事件取 candidates[0].content.parts[0].text。 */
    private void streamGemini(String root, String key, String model, String prompt,
                              TestStreamListener listener) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode contents = body.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", prompt);
        String url = root + "/v1beta/models/" + model + ":streamGenerateContent?alt=sse&key=" + key;
        streamSse(url, body, h -> { }, listener, this::geminiDelta);
    }

    /**
     * 统一 SSE 流式执行：POST stream 请求 → 逐行读 {@code data: <json>} 事件 → 用 deltaParser 取增量文本回调。
     *
     * <p>遵循控制器 SSE 直写模式的对偶：首字节前的失败（连接/4xx/解析空）抛 ProviderProbeException；
     * 首片 delta 已回调后的中断由本方法消化，用已累计文本走 onComplete 收束。{@code data: [DONE]} 收束。</p>
     */
    private void streamSse(String url, JsonNode body,
                           java.util.function.Consumer<HttpHeaders> headers,
                           TestStreamListener listener,
                           java.util.function.Function<String, String> deltaParser) {
        long t0 = System.nanoTime();
        StringBuilder acc = new StringBuilder();
        boolean[] anyDelta = {false};
        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .headers(headers)
                    .body(objectMapper.writeValueAsString(body))
                    .exchange((req, res) -> {
                        int status = res.getStatusCode().value();
                        if (status < 200 || status >= 300) {
                            String err = readAll(res.getBody());
                            throw new ProviderProbeException("上游返回 " + status
                                    + (err.isBlank() ? "" : "：" + truncate(err)));
                        }
                        readSseDeltas(res.getBody(), deltaParser, delta -> {
                            anyDelta[0] = true;
                            acc.append(delta);
                            listener.onDelta(delta);
                        });
                        return null;
                    });
        } catch (ProviderProbeException e) {
            if (!anyDelta[0]) {
                throw e;
            }
            // 首片已发出：中断不外抛，用已累计文本收束（与控制器流式语义一致）。
        } catch (Exception e) {
            if (!anyDelta[0]) {
                throw new ProviderProbeException("模型测试失败：" + rootMessage(e));
            }
        }
        listener.onComplete(new ProviderModelTestResult(elapsedMs(t0), truncate(acc.toString())));
    }

    /** 逐行读 SSE 流：对每个 {@code data:} 负载用 parser 解析增量文本，非空则交给 sink。{@code [DONE]} 终止。 */
    private void readSseDeltas(InputStream in, java.util.function.Function<String, String> parser,
                               java.util.function.Consumer<String> sink) throws java.io.IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring(5).trim();
                if (payload.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(payload)) {
                    break;
                }
                String delta = parser.apply(payload);
                if (delta != null && !delta.isEmpty()) {
                    sink.accept(delta);
                }
            }
        }
    }

    /** OpenAI delta：data 负载里 choices[0].{group}.{field}（如 delta.content）。解析失败返回空串（跳过）。 */
    private String deltaText(String payload, String arr, String obj, String field) {
        try {
            JsonNode node = objectMapper.readTree(payload).path(arr).path(0).path(obj).path(field);
            return node.asText("");
        } catch (Exception e) {
            return "";
        }
    }

    /** Anthropic delta：content_block_delta 事件里 delta.text。解析失败返回空串（跳过）。 */
    private String anthropicDelta(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            return root.path("delta").path("text").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    /** Gemini delta：candidates[0].content.parts[0].text。解析失败返回空串（跳过）。 */
    private String geminiDelta(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload)
                    .path("candidates").path(0).path("content").path("parts").path(0).path("text");
            return node.asText("");
        } catch (Exception e) {
            return "";
        }
    }

    /** 读尽输入流为字符串（错误体用；失败返回空串）。 */
    private String readAll(InputStream in) {
        if (in == null) {
            return "";
        }
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /** 统一 POST：JSON body，自定义头注入，失败归一为 ProviderProbeException（不回显 key）。 */
    private String post(String url, JsonNode body, java.util.function.Consumer<org.springframework.http.HttpHeaders> headers) {
        try {
            return restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
        } catch (ProviderProbeException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderProbeException("模型测试失败：" + rootMessage(e));
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /** 解析 OpenAI choices[0].message.content（截断）。 */
    private String textAt(String body, String arr, int idx, String obj, String field) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body)
                    .path(arr).path(idx).path(obj).path(field);
            return truncate(node.asText(""));
        } catch (Exception e) {
            throw new ProviderProbeException("模型测试响应解析失败");
        }
    }

    /** 解析 Anthropic content[0].text（截断）。 */
    private String anthropicText(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body).path("content").path(0).path("text");
            return truncate(node.asText(""));
        } catch (Exception e) {
            throw new ProviderProbeException("模型测试响应解析失败");
        }
    }

    /** 解析 Gemini candidates[0].content.parts[0].text（截断）。 */
    private String geminiText(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body)
                    .path("candidates").path(0).path("content").path("parts").path(0).path("text");
            return truncate(node.asText(""));
        } catch (Exception e) {
            throw new ProviderProbeException("模型测试响应解析失败");
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= MAX_REPLY_LEN ? s : s.substring(0, MAX_REPLY_LEN) + "…";
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
