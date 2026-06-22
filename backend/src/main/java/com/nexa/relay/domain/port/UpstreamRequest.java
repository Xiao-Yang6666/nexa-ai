package com.nexa.relay.domain.port;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 出站上游请求值对象（RL-1/RL-4/RL-7 第⑤步「调上游」的入参，domain 层，零框架依赖）。
 *
 * <p>领域规则来源：prd-relay RL-4（adapter 转厂商原生协议调上游 {@code Channel.Key + BaseURL}）+
 * RL-7 第⑤步（确定 B + 选中 Channel 后协议转换并调上游）。本 VO 把「向哪个上游、用什么凭证、
 * 发什么报文」收敛为不可变请求，由 {@link UpstreamHttpPort} 的基础设施实现真正发起 HTTP 调用。</p>
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@code baseUrl + path} 拼接出最终 URL（baseUrl 来自 {@code Channel.BaseURL}，path 为相对端点）；</li>
 *   <li>{@code apiKey} 为上游凭证（来自 {@code Channel.Key}，<b>敏感</b>——基础设施实现注入鉴权头，绝不落日志）；</li>
 *   <li>{@code body} 为透传/改写后的请求体原始字节（GET 等无体请求传 {@code null}）；</li>
 *   <li>{@code headers} 为附加/覆写请求头（如 {@code Content-Type}、{@code Accept}），不含鉴权头（鉴权头由实现按 {@code apiKey} 注入）。</li>
 * </ul></p>
 */
public final class UpstreamRequest {

    private final String method;
    private final String baseUrl;
    private final String path;
    private final String apiKey;
    private final byte[] body;
    private final Map<String, String> headers;

    private UpstreamRequest(String method, String baseUrl, String path, String apiKey,
                            byte[] body, Map<String, String> headers) {
        this.method = Objects.requireNonNull(method, "method must not be null");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.apiKey = apiKey;
        this.body = body;
        this.headers = headers == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    /**
     * 构造一笔出站上游请求。
     *
     * @param method  HTTP 方法（如 {@code POST}/{@code GET}，非空）
     * @param baseUrl 上游基础地址（{@code Channel.BaseURL}，非空）
     * @param path    相对端点路径（如 {@code /v1/chat/completions}，非空）
     * @param apiKey  上游凭证（{@code Channel.Key}，敏感，可空——空时不注入鉴权头）
     * @param body    请求体原始字节（可空，无体请求传 null）
     * @param headers 附加/覆写请求头（可空；不含鉴权头）
     * @return 不可变出站请求
     */
    public static UpstreamRequest of(String method, String baseUrl, String path, String apiKey,
                                     byte[] body, Map<String, String> headers) {
        return new UpstreamRequest(method, baseUrl, path, apiKey, body, headers);
    }

    /** @return HTTP 方法（大写，如 {@code POST}） */
    public String method() {
        return method;
    }

    /** @return 上游基础地址（{@code Channel.BaseURL}） */
    public String baseUrl() {
        return baseUrl;
    }

    /** @return 相对端点路径 */
    public String path() {
        return path;
    }

    /** @return 上游凭证（敏感，仅基础设施实现注入鉴权头用，绝不落日志/视图） */
    public String apiKey() {
        return apiKey;
    }

    /** @return 请求体原始字节（可空） */
    public byte[] body() {
        return body;
    }

    /** @return 附加/覆写请求头（不可变，不含鉴权头） */
    public Map<String, String> headers() {
        return headers;
    }
}
