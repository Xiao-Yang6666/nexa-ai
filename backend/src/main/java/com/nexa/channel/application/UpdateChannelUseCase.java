package com.nexa.channel.application;

import com.nexa.channel.domain.exception.ChannelNotFoundException;
import com.nexa.channel.domain.exception.InvalidChannelParameterException;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 编辑渠道用例（应用层，F-2016 PUT /api/channel/，含 F-2020/F-2021/F-2022/F-2025）。
 *
 * <p>用例编排（事务边界）：按 id 加载渠道（缺失抛 404）→ 调聚合 {@code update} 覆盖式更新
 * （字段校验/归一与 key 可选保留逻辑在聚合内充血）→ 保存。openapi 编辑为覆盖式。</p>
 */
@Service
public class UpdateChannelUseCase {

    private final ChannelRepository channelRepository;

    /** @param channelRepository 渠道仓储 */
    public UpdateChannelUseCase(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    /**
     * 编辑渠道。
     *
     * @param command 编辑命令（id/type/models 必填；key 可选保留）
     * @return 更新后的渠道聚合
     * @throws InvalidChannelParameterException id 缺失或字段非法
     * @throws ChannelNotFoundException         渠道不存在
     */
    @Transactional
    public Channel update(UpdateChannelCommand command) {
        if (command.id() == null) {
            throw new InvalidChannelParameterException("id is required");
        }
        Channel channel = channelRepository.findById(command.id())
                .orElseThrow(() -> new ChannelNotFoundException(command.id()));
        channel.update(
                command.type(), command.key(), command.models(), command.name(), command.group(),
                command.priority(), command.weight(), command.autoBan(), command.baseUrl(),
                command.modelMapping(), command.statusCodeMapping(), command.tag(),
                command.setting(), command.channelInfo());
        return channelRepository.save(channel);
    }
}
