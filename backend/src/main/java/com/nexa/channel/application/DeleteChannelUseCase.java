package com.nexa.channel.application;

import com.nexa.channel.domain.exception.ChannelNotFoundException;
import com.nexa.channel.domain.repository.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 删除渠道用例（应用层，F-2016 DELETE /api/channel/{id}）。
 *
 * <p>用例编排（事务边界）：按 id 校验存在（缺失抛 404，不静默忽略——删除不存在渠道应明确报错）
 * → 仓储删除。DB-SCHEMA §3 明确 Channel 不补软删除，本删除为物理删除。</p>
 */
@Service
public class DeleteChannelUseCase {

    private final ChannelRepository channelRepository;

    /** @param channelRepository 渠道仓储 */
    public DeleteChannelUseCase(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    /**
     * 删除渠道。
     *
     * @param id 渠道 id
     * @throws ChannelNotFoundException 渠道不存在
     */
    @Transactional
    public void delete(long id) {
        if (channelRepository.findById(id).isEmpty()) {
            throw new ChannelNotFoundException(id);
        }
        channelRepository.deleteById(id);
    }
}
