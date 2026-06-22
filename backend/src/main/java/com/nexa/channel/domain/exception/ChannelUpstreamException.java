package com.nexa.channel.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 渠道上游集成异常（连通性测试/余额刷新/模型探测/Ollama 管理调用上游失败，→502）。
 *
 * <p>领域规则来源：F-2017 渠道连通性测试、F-2018 余额查询、F-2026 上游模型探测、
 * F-2027 Ollama 管理——这些能力依赖向上游渠道发起真实调用。上游未配置/连接失败/返回错误时
 * 抛本异常，接口层翻译为 502 BadGateway（表达「本服务正常但上游故障」，区别于 4xx 客户端错误）。</p>
 *
 * <p>安全：message 不回显渠道 key 等凭证（凭证剔除在 infra 实现内）。</p>
 */
public class ChannelUpstreamException extends DomainException {

    /** @param message 上游故障描述（无敏感凭证） */
    public ChannelUpstreamException(String message) {
        super("CHANNEL_UPSTREAM_ERROR", message);
    }

    /**
     * @param message 上游故障描述（无敏感凭证）
     * @param cause   底层异常（保留错误链，不吞错；backend-engineer §3.2）
     */
    public ChannelUpstreamException(String message, Throwable cause) {
        super("CHANNEL_UPSTREAM_ERROR", message);
        initCause(cause);
    }
}
