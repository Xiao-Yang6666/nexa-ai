package com.nexa.channel.domain.exception;

/**
 * 渠道不存在异常（按 id 操作但渠道缺失，→404）。
 *
 * <p>领域规则来源：F-2016 渠道详情/编辑/删除、F-2017 单渠道测试、F-2018 单渠道余额更新、
 * F-2026/F-2027 上游探测/Ollama 管理——均按 id 定位渠道，命中失败抛本异常。
 * 接口层翻译为 404 NotFound。</p>
 */
public class ChannelNotFoundException extends DomainException {

    /** @param id 缺失的渠道 id */
    public ChannelNotFoundException(long id) {
        super("CHANNEL_NOT_FOUND", "channel not found: " + id);
    }
}
