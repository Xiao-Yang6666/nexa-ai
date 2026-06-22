package com.nexa.relay.infrastructure.upstream;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Relay 出站 HTTP 客户端配置（REQ-01，绑定前缀 {@code nexa.relay.upstream}）。
 *
 * <p>承载调上游的超时参数（连接 / 读取）。值经 application.yml / 环境变量注入，缺省给出适合
 * LLM 上游的保守值（连接 10s、读取 300s——大模型非流式响应可能较慢）。所有字段单位毫秒。</p>
 */
@ConfigurationProperties(prefix = "nexa.relay.upstream")
public class UpstreamHttpProperties {

    /** 连接超时（毫秒，缺省 10s）。 */
    private int connectTimeoutMs = 10_000;

    /** 读取超时（毫秒，缺省 300s——容纳大模型较慢的非流式响应）。 */
    private int readTimeoutMs = 300_000;

    /** @return 连接超时（毫秒） */
    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    /** @param connectTimeoutMs 连接超时（毫秒） */
    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    /** @return 读取超时（毫秒） */
    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    /** @param readTimeoutMs 读取超时（毫秒） */
    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
