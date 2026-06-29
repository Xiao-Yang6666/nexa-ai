package com.nexa.domain.relay.port;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 上游非流式响应值对象（{@link UpstreamHttpPort} 出参，domain 层，零框架依赖）。
 *
 * <p>领域规则来源：prd-relay RL-7 第⑤/⑧步——承载上游返回的状态码、响应头、响应体原始字节，
 * 供主干做协议回转（passthrough 直通 / IR 反向序列化）、计费 usage 解析、落 Log。本 VO 不可变。</p>
 *
 * <p>响应头以大小写不敏感的键访问（HTTP 头语义）：{@link #header(String)} 取首值；原始多值见
 * {@link #headers()}。状态码 {@link #isSuccessful()} 判定 2xx，供主干决定是否走 RL-3 错误处置（REQ-09）。</p>
 */
public final class UpstreamResponse {

    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    private UpstreamResponse(int statusCode, Map<String, List<String>> headers, byte[] body) {
        this.statusCode = statusCode;
        // 归一为大小写不敏感键（小写），保留多值。
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        if (headers != null) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                normalized.put(e.getKey().toLowerCase(Locale.ROOT),
                        e.getValue() == null ? List.of() : List.copyOf(e.getValue()));
            }
        }
        this.headers = Collections.unmodifiableMap(normalized);
        this.body = body;
    }

    /**
     * 构造一笔上游响应。
     *
     * @param statusCode 上游 HTTP 状态码
     * @param headers    上游响应头（多值，键大小写不敏感；可空）
     * @param body       响应体原始字节（可空）
     * @return 不可变上游响应
     */
    public static UpstreamResponse of(int statusCode, Map<String, List<String>> headers, byte[] body) {
        return new UpstreamResponse(statusCode, headers, body);
    }

    /** @return 上游 HTTP 状态码 */
    public int statusCode() {
        return statusCode;
    }

    /** @return 是否为 2xx 成功响应（供 RL-3 错误处置判定） */
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    /** @return 上游响应头（不可变，键已归一小写，多值） */
    public Map<String, List<String>> headers() {
        return headers;
    }

    /**
     * 取指定响应头首值（大小写不敏感）。
     *
     * @param name 头名（大小写不敏感）
     * @return 命中返回首值，否则 empty
     */
    public Optional<String> header(String name) {
        if (name == null) {
            return Optional.empty();
        }
        List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(0));
    }

    /** @return 响应体原始字节（可空） */
    public byte[] body() {
        return body;
    }
}
