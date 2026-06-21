package com.nexa.nfr.domain.vo;

/**
 * 缓存命中率统计（值对象，不可变）——F-5014 缓存命中率监控，NFR-E02。
 *
 * <p>承载 channel/token/Ability 等热点缓存的命中统计，并对照阈值给出达标判定。领域规则来源：
 * BACKLOG T-218 F-5014 验收「命中率 ≥95% 且 DB 选渠占比 &lt;5%」、API-ENDPOINTS §14.4 F-5014。
 * 「命中率高」与「回源 DB 选渠占比低」是同一枚硬币两面（命中率 95% ⇔ 回源 5%），本值对象把这两条
 * 阈值与判定收敛在一起，供监控/告警引用（充血，backend-engineer §2.2）。横切落地：缓存层（基础设施）
 * 采集命中/未命中计数，统计复用 §9.3 性能指标，本 BC 不挂端点。</p>
 *
 * @param hits   命中次数（&gt;=0）
 * @param misses 未命中（回源 DB）次数（&gt;=0）
 */
public record CacheHitStats(long hits, long misses) {

    /** F-5014 命中率达标下限（95%）。 */
    public static final double HIT_RATE_FLOOR = 0.95;

    /** F-5014 回源 DB 占比上限（5%）。 */
    public static final double DB_FALLBACK_CEILING = 0.05;

    /**
     * 紧凑构造校验：计数非负。
     *
     * @throws IllegalArgumentException 计数为负
     */
    public CacheHitStats {
        if (hits < 0 || misses < 0) {
            throw new IllegalArgumentException("cache hit/miss counts must be >= 0");
        }
    }

    /** @return 总访问次数（命中 + 未命中） */
    public long total() {
        return hits + misses;
    }

    /**
     * 命中率（命中 / 总数）。
     *
     * <p>无访问（total=0）时约定返回 1.0（无回源即视为完美命中，避免除零并不误报告警）。</p>
     *
     * @return 命中率（小数，[0,1]）
     */
    public double hitRate() {
        long t = total();
        return t == 0 ? 1.0 : (double) hits / t;
    }

    /**
     * 回源 DB 占比（未命中 / 总数）。
     *
     * @return 回源占比（小数，[0,1]）
     */
    public double dbFallbackRate() {
        long t = total();
        return t == 0 ? 0.0 : (double) misses / t;
    }

    /**
     * 是否达标（命中率 ≥95% 且回源占比 &lt;5%，F-5014 验收）。
     *
     * <p>两条本质等价（命中率+回源率=1），同时校验以兼容浮点边界并表达验收的双重表述。</p>
     *
     * @return 达标返回 {@code true}
     */
    public boolean isHealthy() {
        return hitRate() >= HIT_RATE_FLOOR && dbFallbackRate() < DB_FALLBACK_CEILING;
    }
}
