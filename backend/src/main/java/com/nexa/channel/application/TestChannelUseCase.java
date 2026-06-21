package com.nexa.channel.application;

import com.nexa.channel.application.port.ChannelProbeClient;
import com.nexa.channel.domain.exception.ChannelNotFoundException;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.model.ChannelTestResult;
import com.nexa.channel.domain.repository.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 渠道连通性测试用例（应用层，F-2017 GET /api/channel/test[/{id}]）。
 *
 * <p>用例编排（事务边界）：单渠道测试先校验存在 + 同步可测护栏（异步渠道抛 400）→ 经端口
 * {@link ChannelProbeClient} 发探测 → 回写 test_time/response_time（聚合 {@code recordTestResult}）→ 保存。
 * 全量测试遍历全部渠道，跳过异步渠道（不可同步测），逐个测并回写，返回结果汇总。</p>
 */
@Service
public class TestChannelUseCase {

    private final ChannelRepository channelRepository;
    private final ChannelProbeClient probeClient;

    /**
     * @param channelRepository 渠道仓储
     * @param probeClient       上游探测端口
     */
    public TestChannelUseCase(ChannelRepository channelRepository, ChannelProbeClient probeClient) {
        this.channelRepository = channelRepository;
        this.probeClient = probeClient;
    }

    /**
     * 单渠道连通性测试（F-2017）。
     *
     * @param id    渠道 id
     * @param model 指定测试模型（可空→渠道默认）
     * @return 测试结果
     * @throws ChannelNotFoundException                                              渠道不存在
     * @throws com.nexa.channel.domain.exception.ChannelOperationNotSupportedException 异步渠道不可同步测
     * @throws com.nexa.channel.domain.exception.ChannelUpstreamException             上游故障
     */
    @Transactional
    public ChannelTestResult test(long id, String model) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ChannelNotFoundException(id));
        channel.ensureSyncTestable();
        ChannelTestResult result = probeClient.test(channel, model);
        channel.recordTestResult((int) Math.round(result.timeMs()), Instant.now());
        channelRepository.save(channel);
        return result;
    }

    /**
     * 全量渠道测试（F-2017）。
     *
     * <p>遍历全部渠道，跳过异步渠道（不可同步测）。单个渠道上游故障不中断整体（捕获并以失败结果记入
     * 汇总，不吞错——失败信息进结果 message），保证一次全量测试覆盖所有可测渠道。</p>
     *
     * @return 各可测渠道的测试结果汇总（保序）
     */
    @Transactional
    public List<ChannelTestResult> testAll() {
        List<ChannelTestResult> results = new ArrayList<>();
        for (Channel channel : channelRepository.findAll()) {
            if (channel.type().isAsync()) {
                continue;
            }
            try {
                ChannelTestResult result = probeClient.test(channel, null);
                channel.recordTestResult((int) Math.round(result.timeMs()), Instant.now());
                channelRepository.save(channel);
                results.add(result);
            } catch (RuntimeException ex) {
                // 单渠道失败不中断全量：记为失败结果（含原因），继续测其余渠道。
                results.add(ChannelTestResult.failure(0.0, ex.getMessage()));
            }
        }
        return results;
    }
}
