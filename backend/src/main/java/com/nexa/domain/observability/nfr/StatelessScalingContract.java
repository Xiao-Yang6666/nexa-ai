package com.nexa.domain.observability.nfr;

/**
 * 无状态横扩契约（设计约束标注，F-5013 无状态横扩与共享缓存，NFR-E01）。
 *
 * <p>本类不含可执行逻辑，是把「无状态横扩」这条横切架构约束<b>固化进代码库</b>的契约标注
 * （API-ENDPOINTS §14.4 F-5013 明确「无独立 REST 端点，由 Redis/缓存层基础设施实现」）。后端各 BC
 * 在实现涉及跨请求状态的功能（会话、选渠亲和、限流计数、OAuth state 等）时，必须遵守本契约，
 * 把状态外置到共享缓存而非实例本地内存。</p>
 *
 * <h2>契约条款（领域规则来源：BACKLOG T-217 F-5013，验收「2~4 实例吞吐近线性增长」）</h2>
 * <ol>
 *   <li><b>实例无本地可变会话状态</b>：会话/令牌鉴权基于无状态 JWT（见 account UserController.logout
 *       的设计注释），不在实例内存维护会话表。</li>
 *   <li><b>共享缓存承载热点状态</b>：channel/token/Ability 缓存、选渠亲和（routing AffinityCacheRepository）、
 *       OAuth state（当前 InMemoryOAuthStateStore 为单实例占位，横扩前须切 Redis 实现，见下方迁移说明）、
 *       限流计数等一律走共享缓存（Redis），任一实例读写同一份。</li>
 *   <li><b>请求可由任一实例处理</b>：无会话粘连（sticky session）依赖；负载均衡轮询即可，实例可任意增减。</li>
 *   <li><b>幂等与并发安全</b>：写共享状态用原子操作/CAS，避免多实例竞态（如 OAuth state 一次性消费）。</li>
 * </ol>
 *
 * <h2>当前已知的横扩前置项（TODO，部署/后续 wave 收口）</h2>
 * <ul>
 *   <li>{@code com.nexa.infrastructure.account.messaging.InMemoryOAuthStateStore} 为单实例内存实现，
 *       多实例部署前须替换为 Redis 实现（否则一个实例发起、另一个实例回调时 state 取不到）。</li>
 *   <li>缓存命中率监控（F-5014）的统计需跨实例聚合，见 {@code CacheHitRateMonitor} 端口。</li>
 * </ul>
 *
 * <p>本标注与 {@code CacheHitRateMonitor} 端口共同构成无状态横扩的「契约 + 观测面」——契约规定怎么做，
 * 命中率监控观测做得好不好（命中率即共享缓存有效性的直接指标）。</p>
 */
public final class StatelessScalingContract {

    private StatelessScalingContract() {
        // 纯契约标注，不实例化。
    }

    /** 验收口径：实例数翻倍时吞吐应近线性增长的最低增长系数（2 实例相对 1 实例 ≥ 此值视为达标）。 */
    public static final double NEAR_LINEAR_SCALING_FACTOR = 1.8;

    /**
     * 判定一次横扩压测是否满足「吞吐近线性增长」（F-5013 验收）。
     *
     * <p>领域规则：N 实例吞吐 / 1 实例吞吐 ≥ {@code N * NEAR_LINEAR_SCALING_FACTOR / 2} 视为近线性
     * （以 2 实例 ≥1.8x 为基准外推）。粗粒度判定，供横扩演练快速校验是否存在共享状态瓶颈。</p>
     *
     * @param singleInstanceRps 单实例稳态吞吐
     * @param scaledRps         N 实例稳态吞吐
     * @param instanceCount     实例数（&gt;=2）
     * @return 近线性达标返回 {@code true}
     */
    public static boolean isNearLinear(double singleInstanceRps, double scaledRps, int instanceCount) {
        if (instanceCount < 2 || singleInstanceRps <= 0) {
            return false;
        }
        double expectedFloor = singleInstanceRps * instanceCount * (NEAR_LINEAR_SCALING_FACTOR / 2.0);
        return scaledRps >= expectedFloor;
    }
}
