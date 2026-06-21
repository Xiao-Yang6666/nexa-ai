package com.nexa.channel.application;

import com.nexa.channel.domain.repository.ChannelRepository;
import com.nexa.channel.domain.vo.Pagination;
import org.springframework.stereotype.Service;

/**
 * 渠道搜索用例（应用层，F-2016 GET /api/channel/search）。
 *
 * <p>用例编排：归一分页 → 仓储关键词分页搜索 + 计数 → 组装 {@link ChannelPage}。
 * 关键词匹配名称/模型/分组/标签（大小写不敏感），匹配逻辑在仓储查询，本用例只编排。</p>
 */
@Service
public class SearchChannelsUseCase {

    private final ChannelRepository channelRepository;

    /** @param channelRepository 渠道仓储 */
    public SearchChannelsUseCase(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    /**
     * 按关键词分页搜索渠道。
     *
     * @param keyword    搜索关键词（可空白→全量）
     * @param pagination 分页参数
     * @return 渠道分页结果（items + total）
     */
    public ChannelPage search(String keyword, Pagination pagination) {
        return new ChannelPage(
                channelRepository.search(keyword, pagination),
                channelRepository.countSearch(keyword));
    }
}
