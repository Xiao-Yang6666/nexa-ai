package com.nexa.channel.infrastructure.probe;

import com.nexa.channel.application.ChannelCacheReinitializer;
import org.springframework.stereotype.Component;

/**
 * 渠道缓存重建端口的占位实现（基础设施层 stub adapter，F-4045 InitChannelCache）。
 *
 * <p><b>切片边界说明（诚实标注）</b>：本片（W2）聚焦渠道管理 + Codex 用量查询领域与编排落地。
 * 真实的选渠路由缓存（W2 亲和缓存 / W3 转发链路的渠道池缓存）尚未在本片承载，故本类先以空操作
 * 占位 InitChannelCache 语义——保证 {@link QueryCodexUsageUseCase} 凭证刷新副作用链路编译/编排闭环。
 * 真实接入时仅替换本 adapter（重建对应渠道的内存路由缓存），应用层无需改动（DDD 防腐层价值）。</p>
 */
@Component
public class StubChannelCacheReinitializer implements ChannelCacheReinitializer {

    /** {@inheritDoc} */
    @Override
    public void reinitialize(Long channelId) {
        // 占位：空操作。真实现重建该渠道在选渠路由缓存中的条目（凭证刷新后保持缓存一致）。
    }
}
