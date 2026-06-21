package com.nexa.nfr.application.port;

import com.nexa.nfr.domain.vo.CacheHitStats;

/**
 * 缓存命中率采集端口（应用层定义，基础设施层实现）——F-5013 无状态横扩与共享缓存 / F-5014 缓存命中率监控。
 *
 * <p>F-5013/F-5014 是「无独立 REST 端点」的横切约束：转发实例无本地状态、共享缓存（Redis）承载会话/选渠
 * 等热点数据（无状态横扩前提），缓存层采集命中/未命中计数。监控用例/告警通过本端口拉取各热点缓存的
 * {@link CacheHitStats}，判定是否满足命中率阈值（F-5014），不直接耦合具体缓存实现（依赖倒置，
 * backend-engineer §2.3）。统计展示复用 §9.3 性能指标 / {@code /metrics}（F-5010）。</p>
 *
 * <p>无状态横扩（F-5013）的领域契约：实例不得持有「跨请求的本地可变状态」（会话、选渠亲和、限流计数等
 * 一律落共享缓存），保证 2~4 实例可任意增减、请求可被任一实例处理（吞吐近线性）。本端口的存在即体现
 * 「状态在共享缓存、实例只读写它」——命中率监控是无状态横扩健康度的直接观测面。</p>
 */
public interface CacheHitRateMonitor {

    /**
     * 已被监控的热点缓存名集（如 {@code "channel"}/{@code "token"}/{@code "ability"}）。
     *
     * @return 缓存名集合（可空集合表示当前无监控项）
     */
    java.util.Set<String> monitoredCaches();

    /**
     * 拉取指定热点缓存的命中统计快照。
     *
     * @param cacheName 缓存名（须为 {@link #monitoredCaches()} 中之一）
     * @return 该缓存的命中统计（实现保证非 null；未知缓存返回零统计）
     */
    CacheHitStats statsOf(String cacheName);
}
