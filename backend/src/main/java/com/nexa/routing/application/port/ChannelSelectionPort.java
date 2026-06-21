package com.nexa.routing.application.port;

import com.nexa.routing.domain.vo.ChannelCandidate;

/**
 * CH-2 选渠委托端口（应用层定义，由 com.nexa.channel 选渠实现适配，F-2035 跨组重试调度依赖）。
 *
 * <p>领域规则来源：FC-072 {@code service/channel_select.go CacheGetRandomSatisfiedChannel} 的内层
 * 「给定 (group, model, priorityRetry) 在 Ability 表筛满足渠道 + 按 Priority/Weight 抽签选一个」逻辑（CH-2）。
 * {@link com.nexa.routing.application.ResolveChannelRouteUseCase} 在 auto 分组跨组重试调度（CH-5）中，
 * 通过本端口委托实际的渠道查询与加权随机抽签——把「选哪个组、该不该切」的调度决策（CH-5）与
 * 「在一个组里怎么选一个渠道」的查询/抽签（CH-2）解耦：</p>
 *
 * <ul>
 *   <li>解耦后 CH-5 调度逻辑（{@link com.nexa.routing.domain.service.CrossGroupRetryScheduler}）零框架依赖、可纯单测。</li>
 *   <li>CH-2 选渠的 DB 查询/权重算法由 channel 上下文实现（W2 channel-crud 片或 relay 片提供 adapter）。</li>
 * </ul>
 *
 * <p>DDD 端口：应用层声明所需能力，具体实现在基础设施/其他 bounded context（依赖倒置，
 * backend-engineer §2.3）。本端口为应用层（非 domain）端口——因为它跨 bounded context 引用
 * channel 选渠实现，而非纯领域抽象。</p>
 */
public interface ChannelSelectionPort {

    /**
     * 在指定分组下按优先级层级选择一个满足渠道（CH-2 优先级分层 + 权重随机）。
     *
     * <p>实现侧从 Ability/Channel 筛出满足 {@code (group, model, enabled=true)} 的渠道集合，
     * 按 priorityRetry 对应的优先级层级做加权随机抽签，返回选中渠道快照；
     * 该层级无满足渠道返回 null（调用方 CH-5 调度据此降级/切组）。</p>
     *
     * @param group         分组名
     * @param model         请求模型名
     * @param priorityRetry 当前优先级重试层级（0=最高优先级层，逐级降级）
     * @return 选中渠道候选快照，无满足渠道返回 null
     */
    ChannelCandidate selectChannel(String group, String model, int priorityRetry);
}
