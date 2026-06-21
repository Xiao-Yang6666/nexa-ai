package com.nexa.routing.infrastructure.selection;

import com.nexa.routing.application.port.ChannelSelectionPort;
import com.nexa.routing.domain.vo.ChannelCandidate;
import org.springframework.stereotype.Component;

/**
 * CH-2 选渠委托端口的占位实现（基础设施层 stub adapter，F-2035 跨组重试调度依赖）。
 *
 * <p><b>切片边界说明（诚实标注）</b>：本片（W2 亲和缓存 + 跨组重试）聚焦选渠中间件的<b>调度决策</b>逻辑
 * （CH-4 亲和判定 + CH-5「选哪个组、该不该切、给不给渠道」），并通过 {@link ChannelSelectionPort} 端口
 * 把「在一个组里按 Priority/Weight 抽签选一个渠道」的实际 DB 查询与加权随机（CH-2）解耦委托出去。
 * CH-2 的真实实现（查 Ability/Channel 表 + 加权随机抽签）属于 channel 选渠/relay 转发片的职责，
 * 按「功能切小、优先保证编译通过」原则，本类先以占位实现承载端口契约：</p>
 *
 * <ul>
 *   <li>{@link #selectChannel}：恒返回 null（视为「该层级无满足渠道」）——使
 *       {@link com.nexa.routing.application.ResolveChannelRouteUseCase} 与
 *       {@link com.nexa.routing.domain.service.CrossGroupRetryScheduler} 的调度流水线可被装配、可启动、可单测，
 *       但首步即判「无可用渠道」（exhausted），不会误返不存在的渠道 id。</li>
 * </ul>
 *
 * <p>真实接入时仅替换本 adapter（实现真实的 Ability 查询 + Priority 分层 + Weight 加权随机），
 * 应用层 {@code ResolveChannelRouteUseCase} 与领域服务 {@code CrossGroupRetryScheduler} 无需改动
 * （DDD 防腐层价值，backend-engineer §2.3）。</p>
 */
@Component
public class StubChannelSelectionAdapter implements ChannelSelectionPort {

    /** {@inheritDoc} */
    @Override
    public ChannelCandidate selectChannel(String group, String model, int priorityRetry) {
        // 占位：返回 null（该层级无满足渠道）。真实现：在 group 下查满足 (model, enabled) 的 Ability，
        // 取 priorityRetry 对应优先级层级，按 Weight 加权随机抽签返回一个 ChannelCandidate。
        return null;
    }
}
