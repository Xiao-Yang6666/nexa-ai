package com.nexa.application.relay;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Relay 转发结果（应用层出参，承载回客户的 status + headers + body，供接口层映射为 HTTP 响应）。
 *
 * <p>领域规则来源：prd-relay RL-7 第⑧步（响应按 inFmt 转回客户）。本 VO 把「最终回客户的响应」
 * 与 Web 框架解耦——应用层产出协议无关的结果，接口层 {@code RelayController} 负责落成
 * {@code ResponseEntity}。非流式路径；流式（SSE）回写留 REQ-08。</p>
 *
 * @param statusCode 回客户的 HTTP 状态码（passthrough 路径 = 上游状态码）
 * @param headers    回客户的响应头（已过滤 hop-by-hop 头，多值；不可变）
 * @param body       响应体原始字节（可空）
 */
public record RelayForwardResult(int statusCode, Map<String, List<String>> headers, byte[] body) {

    public RelayForwardResult {
        headers = headers == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }
}
