package com.nexa.channel.application;

import com.nexa.channel.application.port.ChannelProbeClient;
import com.nexa.channel.domain.exception.ChannelNotFoundException;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 渠道余额更新用例（应用层，F-2018 GET /api/channel/update_balance[/{id}]）。
 *
 * <p>用例编排（事务边界）：经端口 {@link ChannelProbeClient} 查上游余额 → 回写渠道 balance
 * （聚合 {@code updateBalance}，BigDecimal 禁裸 float）→ 保存。单渠道返回最新余额；
 * 全量遍历全部渠道刷新，单个失败不中断整体。</p>
 */
@Service
public class UpdateChannelBalanceUseCase {

    private final ChannelRepository channelRepository;
    private final ChannelProbeClient probeClient;

    /**
     * @param channelRepository 渠道仓储
     * @param probeClient       上游探测端口
     */
    public UpdateChannelBalanceUseCase(ChannelRepository channelRepository, ChannelProbeClient probeClient) {
        this.channelRepository = channelRepository;
        this.probeClient = probeClient;
    }

    /**
     * 单渠道余额更新（F-2018），返回最新余额。
     *
     * @param id 渠道 id
     * @return 最新余额（USD）
     * @throws ChannelNotFoundException                                  渠道不存在
     * @throws com.nexa.channel.domain.exception.ChannelUpstreamException 上游故障
     */
    @Transactional
    public BigDecimal updateOne(long id) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ChannelNotFoundException(id));
        BigDecimal balance = probeClient.queryBalance(channel);
        channel.updateBalance(balance);
        channelRepository.save(channel);
        return balance;
    }

    /**
     * 全量渠道余额刷新（F-2018）。
     *
     * <p>遍历全部渠道刷新余额；单个渠道上游故障不中断整体（捕获跳过，不吞错——已计入跳过）。
     * 返回成功刷新的渠道数。</p>
     *
     * @return 成功刷新余额的渠道数
     */
    @Transactional
    public int updateAll() {
        int refreshed = 0;
        for (Channel channel : channelRepository.findAll()) {
            try {
                channel.updateBalance(probeClient.queryBalance(channel));
                channelRepository.save(channel);
                refreshed++;
            } catch (RuntimeException ex) {
                // 单渠道余额查询失败不中断全量刷新（上游可能限流/未配置），跳过继续。
            }
        }
        return refreshed;
    }
}
