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
 * <p>本期仅定义非流式 {@link #send(UpstreamRequest)}；流式响应体 stream（SSE 逐 chunk 回写）
 * 留作 REQ-08 扩展位（届时新增流式方法，不破坏本接口）。</p>
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
}
