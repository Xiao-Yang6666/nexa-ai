package com.nexa.routing.application;

import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
import com.nexa.relay.domain.exception.NoAvailableChannelException;
import com.nexa.routing.application.port.ChannelSelectionPort;
import com.nexa.routing.domain.vo.ChannelCandidate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 选渠应用服务门面（relay 转发主干在「选哪个渠道」处调用的精简入口，REQ-03，PRD RL-7 第④步）。
 *
 * <p>本用例把选渠子系统封装成 relay 主干可直接消费的干净接口：输入 {@code (UsingGroup, 上游模型名 B)}，
 * 输出一个<b>完整 {@link Channel} 聚合</b>（含 baseURL/key/models 等转发所需字段），而非仅渠道 id。
 * 它桥接两件事：</p>
 * <ol>
 *   <li><b>选 id（路由）</b>：委托 {@link ChannelSelectionPort}（生产实现
 *       {@code AbilityBackedChannelSelectionAdapter}）按 Ability 表 {@code group×model} 候选做
 *       优先级分层 + Weight 加权随机抽签（CH-2 / RL-1，FC-072）。</li>
 *   <li><b>取聚合</b>：用选中 id 经 {@link ChannelRepository} 装配完整渠道聚合，供主干读 baseURL/key 调上游。</li>
 * </ol>
 *
 * <p>设计取舍（与既有真实组件的关系）：</p>
 * <ul>
 *   <li><b>加权随机（真实）</b>：直接复用 {@code AbilityBackedChannelSelectionAdapter} 的 group×B 加权随机抽签。</li>
 *   <li><b>CH-5 重试切换（真实）</b>：{@link #selectChannel(String, String, Set)} 重载支持排除「已尝试渠道」
 *       后再选下一个；排除集语义落到 {@link ChannelSelectionPort#selectChannel(String, String, int, Set)}。
 *       主干的重试循环（由 REQ-10/REQ-09 编排）每次失败把坏渠道 id 加入排除集再调本方法即可。</li>
 *   <li><b>CH-4 同会话亲和（接口预留）</b>：完整亲和缓存（粘连命中/成功回写）由 {@link ResolveChannelRouteUseCase}
 *       承载，但其需 {@code AffinityRequestContext} 等请求上下文。本门面聚焦「无亲和的加权随机 + 重试切换」
 *       最小闭环，亲和作为后续接缝由主干在具备请求上下文后改走 {@link ResolveChannelRouteUseCase} 接入，
 *       本门面签名不变。</li>
 * </ul>
 *
 * <p>不吞错：无候选 / 选中 id 已不存在 → 上抛 {@link NoAvailableChannelException}（RL-1 §4，503，
 * message 不含上游凭证/渠道敏感信息）。本用例放应用层——跨 routing（选渠）与 channel（聚合装配）
 * 两个限界上下文做薄编排，自身无业务规则（规则在 Ability 适配器的抽签算法里）。</p>
 */
@Service
public class SelectRelayChannelUseCase {

    private final ChannelSelectionPort channelSelectionPort;
    private final ChannelRepository channelRepository;

    /**
     * @param channelSelectionPort CH-2 选渠委托端口（生产注入 Ability 加权随机适配器）
     * @param channelRepository    渠道仓储（按选中 id 装配完整聚合）
     */
    public SelectRelayChannelUseCase(ChannelSelectionPort channelSelectionPort,
                                     ChannelRepository channelRepository) {
        this.channelSelectionPort = channelSelectionPort;
        this.channelRepository = channelRepository;
    }

    /**
     * 选渠（首次，无重试排除）：按 {@code (group, 上游模型 B)} 加权随机选一个渠道并装配完整聚合。
     *
     * @param group         token 使用分组（UsingGroup）
     * @param upstreamModel 上游真实模型名 B（两层映射 C→A→B 后的产物）
     * @return 选中的完整渠道聚合（含 baseURL/key/models）
     * @throws NoAvailableChannelException 无满足渠道，或选中 id 在仓储中已不存在
     */
    public Channel selectChannel(String group, String upstreamModel) {
        return selectChannel(group, upstreamModel, Set.of());
    }

    /**
     * 选渠（CH-5 重试切换）：在 {@link #selectChannel(String, String)} 基础上排除一组已尝试（失败）渠道后再选。
     *
     * <p>主干上游失败重试时调用：把已尝试渠道 id 累加进 {@code excludeChannelIds} 再调本方法，
     * 即可拿到「下一个」未尝试的满足渠道；全部排除后无剩余即上抛无可用渠道（对齐 CH-5 全组耗尽语义）。
     * 售价恒定、成本随新渠道的处理在计费层（REQ-05），本门面只负责「选出不同的渠道」。</p>
     *
     * @param group            token 使用分组（UsingGroup）
     * @param upstreamModel    上游真实模型名 B
     * @param excludeChannelIds 已尝试需排除的渠道 id 集合（null/空=不排除，等价首次选渠）
     * @return 选中的完整渠道聚合（排除集外的下一个满足渠道）
     * @throws NoAvailableChannelException 排除后无满足渠道，或选中 id 在仓储中已不存在
     */
    public Channel selectChannel(String group, String upstreamModel, Set<Long> excludeChannelIds) {
        if (group == null || group.isBlank() || upstreamModel == null || upstreamModel.isBlank()) {
            throw new NoAvailableChannelException("group and upstream model must not be blank for channel selection");
        }
        // CH-2 加权随机抽签（priorityRetry=0 最高优先级层；重试降级层由主干重试循环结合排除集推进）。
        ChannelCandidate candidate =
                channelSelectionPort.selectChannel(group, upstreamModel, 0, excludeChannelIds);
        if (candidate == null) {
            throw new NoAvailableChannelException("no available channel for group/model selection");
        }
        // 用选中 id 装配完整渠道聚合（主干据此读 baseURL/key 调上游）。
        return channelRepository.findById(candidate.channelId())
                .orElseThrow(() -> new NoAvailableChannelException(
                        "selected channel no longer exists for group/model selection"));
    }
}
