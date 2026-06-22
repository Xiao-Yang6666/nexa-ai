package com.nexa.relay.infrastructure.upstream;

import com.nexa.relay.domain.exception.UpstreamException;
import com.nexa.relay.domain.port.UpstreamHttpPort;
import com.nexa.relay.domain.port.UpstreamRequest;
import com.nexa.relay.domain.port.UpstreamResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Relay 出站上游 HTTP 端口的 Spring {@link RestClient} 实现（基础设施层适配器，REQ-01）。
 *
 * <p>实现 domain 端口 {@link UpstreamHttpPort}：把 {@link UpstreamRequest} 翻译为对上游的真实 HTTP 调用，
 * 拼接 {@code baseUrl + path}、注入 {@code Channel.Key} 鉴权头、透传请求体字节，返回上游响应
 * （status + headers + body）。选型与现网 oauth/ops/deployment 域一致（Spring {@code RestClient}）。</p>
 *
 * <p><b>安全</b>：
 * <ul>
 *   <li>上游凭证（{@link UpstreamRequest#apiKey()}）仅用于注入 {@code Authorization} 头，<b>绝不落日志</b>
 *       （本类不打印任何请求头/凭证）；</li>
 *   <li>异常信息只携带 URL 路径与状态/错误类别，不回显凭证。</li>
 * </ul>
 * SSRF 白名单校验为后续增强位（与 RL-5 {@code ValidateURLWithFetchSetting} 同源，留 REQ 接线点），
 * 当前 baseUrl 来自管理员配置的 {@code Channel.BaseURL}（非客户输入），风险面受控。</p>
 *
 * <p><b>错误语义</b>：HTTP 层 4xx/5xx <b>不</b>抛异常（{@code onStatus} 不拦截），作为正常
 * {@link UpstreamResponse} 返回，供主干按状态码走 RL-3 重试/禁用（REQ-09）；仅网络层失败
 * （连接/读超时、DNS、连接拒绝等取不到 HTTP 响应）wrap 成 {@link UpstreamException}（502，不吞错）。</p>
 *
 * <p>本类无共享可变状态（{@code RestClient} 不可变线程安全），可并发复用。流式留 REQ-08。</p>
 */
@Component
public class RestClientUpstreamHttpAdapter implements UpstreamHttpPort {

    private final RestClient restClient;

    /**
     * @param properties 出站 HTTP 配置（超时；REQ-01 验收要求含超时配置）
     */
    public RestClientUpstreamHttpAdapter(UpstreamHttpProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public UpstreamResponse send(UpstreamRequest request) {
        String url = buildUrl(request.baseUrl(), request.path());
        HttpMethod method = HttpMethod.valueOf(request.method().toUpperCase());
        try {
            RestClient.RequestBodySpec spec = restClient.method(method)
                    .uri(url);

            // 注入透传/覆写头（不含鉴权头；鉴权头单独按 apiKey 注入）。
            for (Map.Entry<String, String> h : request.headers().entrySet()) {
                spec = spec.header(h.getKey(), h.getValue());
            }
            // 注入上游凭证鉴权头（敏感，绝不落日志）。
            String apiKey = request.apiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
            // 默认 JSON content-type（调用方未显式覆写时），透传请求体字节。
            byte[] body = request.body();
            if (body != null) {
                if (!request.headers().containsKey(HttpHeaders.CONTENT_TYPE)) {
                    spec = spec.contentType(MediaType.APPLICATION_JSON);
                }
                spec = spec.body(body);
            }

            // 用 no-op onStatus 覆盖默认 4xx/5xx 抛异常行为：HTTP 层错误作为正常响应返回，
            // 由主干按状态码走 RL-3 重试/禁用（REQ-09）。
            ResponseEntity<byte[]> entity = spec.retrieve()
                    .onStatus(status -> true, (req, res) -> { /* no-op：不抛，保留响应交上层判定 */ })
                    .toEntity(byte[].class);

            return toUpstreamResponse(entity);
        } catch (UpstreamException e) {
            throw e;
        } catch (RuntimeException e) {
            // 网络层失败（连接/读超时、DNS、连接拒绝）：wrap 成 502 上抛（不吞错）。
            // message 仅含 path 与错误类别，绝不含凭证/完整 URL（baseUrl 可能含敏感子域信息）。
            throw new UpstreamException(502,
                    "upstream request failed for path " + request.path() + ": " + e.getClass().getSimpleName());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void stream(UpstreamRequest request, UpstreamStreamHandler handler) {
        String url = buildUrl(request.baseUrl(), request.path());
        HttpMethod method = HttpMethod.valueOf(request.method().toUpperCase());
        try {
            RestClient.RequestBodySpec spec = restClient.method(method).uri(url);
            for (Map.Entry<String, String> h : request.headers().entrySet()) {
                spec = spec.header(h.getKey(), h.getValue());
            }
            // SSE 流式：声明 Accept: text/event-stream，鼓励上游分块返回。
            spec = spec.header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
            String apiKey = request.apiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
            byte[] body = request.body();
            if (body != null) {
                if (!request.headers().containsKey(HttpHeaders.CONTENT_TYPE)) {
                    spec = spec.contentType(MediaType.APPLICATION_JSON);
                }
                spec = spec.body(body);
            }

            // exchange：直接拿到响应体 InputStream，按 SSE 事件边界（空行 \n\n）切块逐块回调。
            spec.exchange((clientReq, clientRes) -> {
                int status = clientRes.getStatusCode().value();
                if (status < 200 || status >= 300) {
                    byte[] errBody = clientRes.getBody().readAllBytes();
                    handler.onError(status, errBody);
                    return null;
                }
                streamByEvent(clientRes.getBody(), handler);
                handler.onComplete(status);
                return null;
            });
        } catch (UpstreamException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new UpstreamException(502,
                    "upstream stream failed for path " + request.path() + ": " + e.getClass().getSimpleName());
        }
    }

    /**
     * 按 SSE 事件边界切块：累积字节直到遇到事件分隔（{@code \n\n}），整事件块回调一次。
     *
     * <p>SSE 规范以空行分隔事件；按事件而非按字节回调可让上层 {@code parseStreamChunk} 拿到完整事件，
     * 避免半个 JSON 被切断。流末残留缓冲（无终止空行）作为最后一块回调。</p>
     */
    private static void streamByEvent(java.io.InputStream in, UpstreamStreamHandler handler) throws java.io.IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            for (int i = 0; i < n; i++) {
                buf.write(chunk[i]);
            }
            // 检测事件边界 \n\n，命中即把累积块整体回调并清空缓冲。
            byte[] cur = buf.toByteArray();
            int boundary = lastDoubleNewline(cur);
            if (boundary >= 0) {
                byte[] event = java.util.Arrays.copyOfRange(cur, 0, boundary + 2);
                handler.onChunk(event);
                byte[] rest = java.util.Arrays.copyOfRange(cur, boundary + 2, cur.length);
                buf.reset();
                buf.write(rest);
            }
        }
        if (buf.size() > 0) {
            handler.onChunk(buf.toByteArray());
        }
    }

    /** 返回缓冲中最后一个 {@code \n\n} 边界的起始下标（无则 -1）。 */
    private static int lastDoubleNewline(byte[] data) {
        for (int i = data.length - 2; i >= 0; i--) {
            if (data[i] == '\n' && data[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
    }

    /**
     * 拼接最终 URL：{@code baseUrl + path}，容错首尾斜杠重复/缺失。
     *
     * @param baseUrl 上游基础地址（{@code Channel.BaseURL}）
     * @param path    相对端点路径
     * @return 拼接后的绝对 URL
     */
    private static String buildUrl(String baseUrl, String path) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        String p = path == null ? "" : path.trim();
        if (b.endsWith("/") && p.startsWith("/")) {
            return b + p.substring(1);
        }
        if (!b.endsWith("/") && !p.startsWith("/") && !b.isEmpty() && !p.isEmpty()) {
            return b + "/" + p;
        }
        return b + p;
    }

    /**
     * 将 {@code RestClient} 响应实体映射为 domain 响应 VO（保留多值响应头）。
     *
     * @param entity 上游响应实体
     * @return domain 上游响应
     */
    private static UpstreamResponse toUpstreamResponse(ResponseEntity<byte[]> entity) {
        Map<String, List<String>> headers = entity.getHeaders();
        return UpstreamResponse.of(entity.getStatusCode().value(), headers, entity.getBody());
    }
}
