package com.nexa.channel.application;

import com.nexa.channel.domain.exception.InvalidChannelParameterException;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
import com.nexa.channel.domain.vo.ChannelStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 按 tag 批量启停渠道用例（应用层，F-2019 POST /api/channel/tag/enable|disable）。
 *
 * <p>用例编排（事务边界）：按 tag 列出渠道 → 批量置目标状态。openapi 标「幂等键 tag」——
 * 同 tag 同操作多次结果一致。enable→ENABLED、disable→MANUALLY_DISABLED（手动禁用不自动恢复）。
 * tag 为空白抛 {@link InvalidChannelParameterException}（→400）。返回受影响渠道数。</p>
 */
@Service
public class ToggleChannelsByTagUseCase {

    private final ChannelRepository channelRepository;

    /** @param channelRepository 渠道仓储 */
    public ToggleChannelsByTagUseCase(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    /**
     * 按 tag 批量启用渠道（F-2019 enable）。
     *
     * @param tag 标签（非空白）
     * @return 受影响渠道数
     * @throws InvalidChannelParameterException tag 空白
     */
    @Transactional
    public int enableByTag(String tag) {
        return toggle(tag, ChannelStatus.ENABLED);
    }

    /**
     * 按 tag 批量禁用渠道（F-2019 disable，手动禁用）。
     *
     * @param tag 标签（非空白）
     * @return 受影响渠道数
     * @throws InvalidChannelParameterException tag 空白
     */
    @Transactional
    public int disableByTag(String tag) {
        return toggle(tag, ChannelStatus.MANUALLY_DISABLED);
    }

    /**
     * 按 tag 切换状态（内部共用）。
     *
     * @param tag    标签（非空白）
     * @param status 目标状态
     * @return 受影响渠道数
     */
    private int toggle(String tag, ChannelStatus status) {
        String t = tag == null ? null : tag.trim();
        if (t == null || t.isEmpty()) {
            throw new InvalidChannelParameterException("tag must not be empty");
        }
        List<Long> ids = channelRepository.findByTag(t).stream().map(Channel::id).toList();
        if (ids.isEmpty()) {
            return 0;
        }
        return channelRepository.updateStatusByIds(ids, status);
    }
}
