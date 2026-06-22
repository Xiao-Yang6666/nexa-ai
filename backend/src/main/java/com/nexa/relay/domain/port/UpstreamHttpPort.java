package com.nexa.relay.domain.port;

/**
 * Relay 出站上游 HTTP 端口（domain 定接口，infrastructure 实现，REQ-01「调上游的物理通道」）。
 *
 * <p>领域规则来源：prd-relay RL-1 §3.5（网关向上游转发）、RL-4（adapter 转厂商原生协议调上游
 * {@code Channel.Key + BaseURL}）、BILLING-MODEL-ARCHITECTURE §6 第12步 Outbound 调上游。
 * 这是 relay 域唯一发起上游请求的能力抽象：应用层/领域服务只依赖本端口，HTTP 客户端选型、
 * 鉴权头注入、超时、连接复用等基础设施细节封装在实现
 * {@code relay.infrastructure.upstream.RestClientUpstreamHttpAdapter}（依赖倒置，backend-engineer §2.3）。</p>
 *
 * <p><b>安全铁律</b>：实现按 {@link UpstreamRequest#apiKey()} 注入上游鉴权头（如
 * {@code Authorization: Bearer <key>}），上游凭证<b>绝不落日志</b>；上游 BaseURL 的 SSRF 防护
 * （白名单/校验，与 RL-5 {@code ValidateURLWithFetchSetting} 同源思路）由实现兜底。</p>
 *
 * <p>本期定义非流式 {@link #send(UpstreamRequest)} 与流式 {@link #stream(UpstreamRequest, UpstreamStreamHandler)}
 * （REQ-08 SSE 逐 chunk 回写）。流式实现按行/事件切分上游响应体，逐块回调 handler，由主干经
 * {@code ProtocolAdapter.parseStreamChunk/serializeStreamChunk} 做 1→N 协议转换后 flush 给客户。</p>
 */
public interface UpstreamHttpPort {

    /**
     * 非流式调用上游：按 {@code baseUrl + path} 拼接 URL、注入 {@code Channel.Key} 鉴权头、
     * 透传请求体字节，同步返回上游响应（status + headers + body）。
     *
     * <p>领域语义：本方法只负责「把请求送到上游并拿回完整响应」，<b>不</b>在此判定上游错误码是否需要
     * 重试/禁用（RL-3 由主干 REQ-09 处理）；网络层失败（连接超时/读超时/DNS 失败等无法取得 HTTP
     * 响应的情形）抛 {@link com.nexa.relay.domain.exception.UpstreamException}（不吞错），HTTP 层错误
     * （4xx/5xx）仍作为正常 {@link UpstreamResponse} 返回供上层按状态码分支处理。</p>
     *
     * @param request 出站请求（含 baseUrl/path/apiKey/body/headers）
     * @return 上游非流式响应（status + headers + body）
     * @throws com.nexa.relay.domain.exception.UpstreamException 网络层失败 / 无法取得 HTTP 响应
     */
    UpstreamResponse send(UpstreamRequest request);

    /**
     * 流式调用上游（REQ-08）：发起请求后按 SSE 事件边界逐块读取上游响应体，每块原始字节回调
     * {@code handler.onChunk(rawChunkBytes)}；上游流正常结束回调 {@code handler.onComplete(statusCode)}。
     *
     * <p>领域语义：本方法只负责「把上游 SSE 原始块按序喂给 handler」，协议转换（{@code parseStreamChunk →
     * IR → serializeStreamChunk}）与回写客户、末尾计费由主干编排。HTTP 非 2xx（上游开流前即报错）以
     * {@code handler.onError(statusCode, rawBody)} 反馈，供主干按 RL-3 处置；网络层失败抛
     * {@link com.nexa.relay.domain.exception.UpstreamException}。</p>
     *
     * @param request 出站请求（body 中 {@code stream=true}）
     * @param handler 逐块回调（onChunk/onComplete/onError）
     * @throws com.nexa.relay.domain.exception.UpstreamException 网络层失败
     */
    void stream(UpstreamRequest request, UpstreamStreamHandler handler);

    /**
     * 流式回调（REQ-08）：主干实现，接收上游 SSE 原始块并做协议转换 + 回写客户。
     */
    interface UpstreamStreamHandler {
        /** 收到一块上游 SSE 原始字节（一个或多个事件）。 */
        void onChunk(byte[] rawChunk);

        /** 上游流正常结束（2xx 完成）。 */
        void onComplete(int statusCode);

        /** 上游开流前报错（HTTP 非 2xx），携带状态码与原始错误体（供 RL-3 处置 + 脱敏）。 */
        void onError(int statusCode, byte[] rawBody);
    }
}
