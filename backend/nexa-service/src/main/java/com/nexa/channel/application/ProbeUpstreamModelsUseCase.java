package com.nexa.channel.application;

import com.nexa.channel.application.port.ChannelProbeClient;
import com.nexa.channel.domain.exception.ChannelNotFoundException;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 上游模型探测与应用用例（应用层，F-2026 fetch_models / upstream/apply）。
 *
 * <p>用例编排（事务边界）：
 * <ul>
 *   <li>{@link #fetch(long)}：经端口探测渠道上游支持的模型集（openapi「预览，不改 Models」，不落库）。</li>
 *   <li>{@link #apply(long, List)}：将勾选模型集覆盖式应用到渠道 models（聚合 {@code applyUpstreamModels}）并保存。</li>
 * </ul>
 * </p>
 */
@Service
public class ProbeUpstreamModelsUseCase {

    private final ChannelRepository channelRepository;
    private final ChannelProbeClient probeClient;

    /**
     * @param channelRepository 渠道仓储
     * @param probeClient       上游探测端口
     */
    public ProbeUpstreamModelsUseCase(ChannelRepository channelRepository, ChannelProbeClient probeClient) {
        this.channelRepository = channelRepository;
        this.probeClient = probeClient;
    }

    /**
     * 探测渠道上游支持的模型集（F-2026 fetch，预览，不落库）。
     *
     * @param id 渠道 id
     * @return 上游模型名列表（保序）
     * @throws ChannelNotFoundException                                  渠道不存在
     * @throws com.nexa.channel.domain.exception.ChannelUpstreamException 上游故障
     */
    public List<String> fetch(long id) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ChannelNotFoundException(id));
        return probeClient.fetchUpstreamModels(channel);
    }

    /**
     * 将勾选模型集覆盖式应用到渠道（F-2026 apply，更新 models）。
     *
     * @param id     渠道 id
     * @param models 勾选的上游模型集（非空）
     * @return 更新后的渠道聚合
     * @throws ChannelNotFoundException                                       渠道不存在
     * @throws com.nexa.channel.domain.exception.InvalidChannelParameterException 模型集为空
     */
    @Transactional
    public Channel apply(long id, List<String> models) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ChannelNotFoundException(id));
        channel.applyUpstreamModels(models);
        return channelRepository.save(channel);
    }
}
