package com.nexa.channel.application;

import com.nexa.channel.domain.exception.ChannelNotFoundException;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
import org.springframework.stereotype.Service;

/**
 * 渠道详情查询用例（应用层，F-2016 渠道详情，供编辑回填/查看）。
 *
 * <p>用例编排：按 id 查渠道 → 命中失败抛 {@link ChannelNotFoundException}（不静默返回 null，
 * 接口层映射 404）。返回领域聚合，接口层裁剪为 AdminView（剔除 key）。</p>
 */
@Service
public class GetChannelUseCase {

    private final ChannelRepository channelRepository;

    /** @param channelRepository 渠道仓储 */
    public GetChannelUseCase(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    /**
     * 按 id 取渠道详情。
     *
     * @param id 渠道 id
     * @return 渠道聚合
     * @throws ChannelNotFoundException 渠道不存在
     */
    public Channel get(long id) {
        return channelRepository.findById(id)
                .orElseThrow(() -> new ChannelNotFoundException(id));
    }
}
