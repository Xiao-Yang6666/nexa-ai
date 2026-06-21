package com.nexa.ops.infrastructure.monitoring;

import com.nexa.ops.application.port.CacheStatsProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.LongAdder;

/**
 * {@link CacheStatsProvider} 的进程内实现（基础设施层，F-4019 cache_stats / F-4021 reset_stats）。
 *
 * <p>用无锁计数器（{@link LongAdder}）累计缓存命中/未命中，供运维端点采样与重置。本实现是
 * 进程级、可被各业务缓存切面调用 {@link #recordHit()}/{@link #recordMiss()} 上报；本片先提供
 * 可观测/可重置的计数底座（真实条目数由具体缓存接入时上报），重置为幂等运维动作。</p>
 *
 * <p>线程安全：{@link LongAdder} 高并发下计数无锁聚合，适合多线程（含虚拟线程）上报。</p>
 */
@Component
public class InMemoryCacheStatsProvider implements CacheStatsProvider {

    private final LongAdder hitCount = new LongAdder();
    private final LongAdder missCount = new LongAdder();
    private final LongAdder entryCount = new LongAdder();

    /** 记录一次缓存命中（供业务缓存切面上报）。 */
    public void recordHit() {
        hitCount.increment();
    }

    /** 记录一次缓存未命中（供业务缓存切面上报）。 */
    public void recordMiss() {
        missCount.increment();
    }

    /**
     * 调整当前条目数（新增正、淘汰负，供业务缓存上报）。
     *
     * @param delta 条目数增量
     */
    public void adjustEntryCount(long delta) {
        entryCount.add(delta);
    }

    /** {@inheritDoc} */
    @Override
    public CacheStats sample() {
        return new CacheStats(hitCount.sum(), missCount.sum(), entryCount.sum());
    }

    /** {@inheritDoc} */
    @Override
    public void resetStats() {
        // 重置命中/未命中计数（条目数反映实时缓存规模，不在此清零，避免与真实缓存失配）。
        hitCount.reset();
        missCount.reset();
    }
}
