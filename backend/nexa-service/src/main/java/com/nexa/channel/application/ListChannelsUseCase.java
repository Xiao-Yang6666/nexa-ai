package com.nexa.channel.application;

import com.nexa.channel.domain.repository.ChannelRepository;
import com.nexa.channel.domain.vo.Pagination;
import org.springframework.stereotype.Service;

/**
 * 渠道列表查询用例（应用层，F-2016 GET /api/channel/）。
 *
 * <p>用例编排：归一分页 → 仓储多条件过滤分页 + 计数 → 组装 {@link ChannelPage}。
 * 薄编排，无领域规则（backend-engineer §2.1）。group/type/tag/status 为可空过滤维度。</p>
 */
@Service
public class ListChannelsUseCase {

    private final ChannelRepository channelRepository;

    /** @param channelRepository 渠道仓储 */
    public ListChannelsUseCase(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    /**
     * 分页 + 多条件过滤列出渠道。
     *
     * @param group      分组过滤（可空）
     * @param type       type 过滤（可空）
     * @param tag        tag 过滤（可空）
     * @param status     状态过滤（可空）
     * @param pagination 分页参数
     * @return 渠道分页结果（items + total）
     */
    public ChannelPage list(String group, Integer type, String tag, Integer status, Pagination pagination) {
        return new ChannelPage(
                channelRepository.findPage(group, type, tag, status, pagination),
                channelRepository.count(group, type, tag, status));
    }
}
