package com.nexa.channel.application;

import com.nexa.channel.application.port.ChannelProbeClient;
import com.nexa.channel.domain.exception.ChannelNotFoundException;
import com.nexa.channel.domain.exception.InvalidChannelParameterException;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
import org.springframework.stereotype.Service;

/**
 * Ollama 模型管理用例（应用层，F-2027 ollama/pull|delete|version）。
 *
 * <p>用例编排：按 id 加载渠道（缺失抛 404）→ 聚合护栏 {@code ensureOllama}（非 Ollama 渠道抛 400）
 * → 经端口 {@link ChannelProbeClient} 执行 Ollama 操作。openapi 标 pull/delete「幂等键
 * (channel_id, model, action)」。pull/delete 不改本地渠道状态（模型在上游 Ollama 实例侧管理）。</p>
 */
@Service
public class ManageOllamaUseCase {

    private final ChannelRepository channelRepository;
    private final ChannelProbeClient probeClient;

    /**
     * @param channelRepository 渠道仓储
     * @param probeClient       上游探测端口
     */
    public ManageOllamaUseCase(ChannelRepository channelRepository, ChannelProbeClient probeClient) {
        this.channelRepository = channelRepository;
        this.probeClient = probeClient;
    }

    /**
     * Ollama 拉取模型（F-2027 pull）。
     *
     * @param id    渠道 id
     * @param model 待拉取模型名（非空白）
     * @throws ChannelNotFoundException                                              渠道不存在
     * @throws com.nexa.channel.domain.exception.ChannelOperationNotSupportedException 非 Ollama 渠道
     * @throws InvalidChannelParameterException                                       model 空白
     */
    public void pull(long id, String model) {
        Channel channel = requireOllamaChannel(id);
        probeClient.ollamaPull(channel, requireModel(model));
    }

    /**
     * Ollama 删除模型（F-2027 delete）。
     *
     * @param id    渠道 id
     * @param model 待删除模型名（非空白）
     * @throws ChannelNotFoundException                                              渠道不存在
     * @throws com.nexa.channel.domain.exception.ChannelOperationNotSupportedException 非 Ollama 渠道
     * @throws InvalidChannelParameterException                                       model 空白
     */
    public void delete(long id, String model) {
        Channel channel = requireOllamaChannel(id);
        probeClient.ollamaDelete(channel, requireModel(model));
    }

    /**
     * Ollama 版本查询（F-2027 version）。
     *
     * @param id 渠道 id
     * @return Ollama 版本号
     * @throws ChannelNotFoundException                                              渠道不存在
     * @throws com.nexa.channel.domain.exception.ChannelOperationNotSupportedException 非 Ollama 渠道
     */
    public String version(long id) {
        Channel channel = requireOllamaChannel(id);
        return probeClient.ollamaVersion(channel);
    }

    /**
     * 加载渠道并校验为 Ollama 类型（共用前置）。
     *
     * @param id 渠道 id
     * @return Ollama 渠道聚合
     */
    private Channel requireOllamaChannel(long id) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ChannelNotFoundException(id));
        channel.ensureOllama();
        return channel;
    }

    /**
     * 校验 model 非空白。
     *
     * @param model 模型名
     * @return 规范化后的模型名
     */
    private static String requireModel(String model) {
        String v = model == null ? null : model.trim();
        if (v == null || v.isEmpty()) {
            throw new InvalidChannelParameterException("model is required");
        }
        return v;
    }
}
