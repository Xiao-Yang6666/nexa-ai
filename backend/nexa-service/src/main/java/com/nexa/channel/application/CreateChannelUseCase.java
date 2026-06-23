package com.nexa.channel.application;

import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建渠道用例（应用层，F-2016 POST /api/channel/，含 F-2020/F-2021/F-2022/F-2025）。
 *
 * <p>用例编排（事务边界）：调聚合工厂 {@link Channel#create} 构造合法渠道（字段校验/归一在聚合内充血）
 * → 仓储保存 → 返回带 id 的聚合。Status=ENABLED 默认启用由聚合工厂保证（openapi 约定）。</p>
 */
@Service
public class CreateChannelUseCase {

    private final ChannelRepository channelRepository;

    /** @param channelRepository 渠道仓储 */
    public CreateChannelUseCase(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    /**
     * 创建渠道。
     *
     * @param command 创建命令（type/key/models 必填）
     * @return 持久化后的渠道（含 id）
     * @throws com.nexa.channel.domain.exception.InvalidChannelParameterException 字段非法
     */
    @Transactional
    public Channel create(CreateChannelCommand command) {
        Channel channel = Channel.create(
                command.type(), command.key(), command.models(), command.name(), command.group(),
                command.priority(), command.weight(), command.autoBan(), command.baseUrl(),
                command.modelMapping(), command.statusCodeMapping(), command.tag(),
                command.setting(), command.channelInfo());
        return channelRepository.save(channel);
    }
}
