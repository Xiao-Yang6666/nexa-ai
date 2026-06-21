package com.nexa.routing.domain.vo;

import com.nexa.routing.domain.exception.InvalidAffinityParameterException;

/**
 * 渠道亲和缓存全局策略值对象（不可变，F-2031，PRD CH-4「ChannelAffinitySetting」）。
 *
 * <p>领域规则来源：FC-068 {@code channel_affinity_setting.go}。承载亲和功能的全局开关与缓存策略：
 * <ul>
 *   <li>{@link #enabled} —— 总开关。关闭后所有亲和规则都不参与选渠（CH-4 §2 前置条件）。</li>
 *   <li>{@link #switchOnSuccess} —— 仅请求成功才回写/续期会话键→渠道映射（PRD CH-4 §3 节点 af_switch）。
 *       false=任何调用都不回写（仅命中已有缓存复用）；true=成功才回写。</li>
 *   <li>{@link #maxEntries} —— 缓存最大条目数（>=1，超出按 LRU 等策略由 infra 实现淘汰）。</li>
 *   <li>{@link #defaultTtlSeconds} —— 缓存键→渠道映射默认 TTL（秒，>=1）；规则未覆盖时回落本值。</li>
 * </ul>
 * 按值相等、构造后不可变（值对象）。变更通过新建实例替换（配置覆盖式 PUT /api/option）。</p>
 *
 * @param enabled            亲和功能总开关
 * @param switchOnSuccess    成功才回写缓存（true）/ 总不回写（false）
 * @param maxEntries         缓存最大条目数（>=1）
 * @param defaultTtlSeconds  默认 TTL 秒（>=1）
 */
public record AffinitySettings(boolean enabled, boolean switchOnSuccess, int maxEntries, long defaultTtlSeconds) {

    /** 推荐缺省最大条目数（FC-068 现网默认 100000）。 */
    public static final int DEFAULT_MAX_ENTRIES = 100_000;

    /** 推荐缺省 TTL（FC-068 现网默认 3600s = 1h）。 */
    public static final long DEFAULT_TTL_SECONDS = 3600L;

    /**
     * 紧凑构造器：校验不变量（maxEntries/defaultTtlSeconds 至少 1）。
     *
     * @throws InvalidAffinityParameterException 字段非法
     */
    public AffinitySettings {
        if (maxEntries < 1) {
            throw new InvalidAffinityParameterException("max_entries must be >= 1");
        }
        if (defaultTtlSeconds < 1) {
            throw new InvalidAffinityParameterException("default_ttl_seconds must be >= 1");
        }
    }

    /**
     * 缺省策略（功能开启 + 成功回写 + 现网默认上限/TTL）。
     *
     * @return 缺省策略
     */
    public static AffinitySettings defaults() {
        return new AffinitySettings(true, true, DEFAULT_MAX_ENTRIES, DEFAULT_TTL_SECONDS);
    }
}
