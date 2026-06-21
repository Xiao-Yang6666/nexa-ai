package com.nexa.routing.domain.vo;

import com.nexa.routing.domain.exception.InvalidAffinityParameterException;

import java.time.Instant;

/**
 * 亲和缓存条目值对象（不可变，F-2029/F-2031/F-2033，PRD CH-4「会话键→渠道映射」）。
 *
 * <p>领域规则来源：FC-068。一条缓存记录是「(rule_name, key_fp, using_group) → channel_id」，
 * 含命中次数、最近命中时间与过期时刻：
 * <ul>
 *   <li>{@link #channelId} —— 上次成功的渠道 id（粘连目标）。</li>
 *   <li>{@link #hitCount} —— 命中次数（F-2033 用量统计；每次复用 +1）。</li>
 *   <li>{@link #lastHitAt} —— 最近一次命中时刻（F-2033 用量统计）。</li>
 *   <li>{@link #expiresAt} —— 过期时刻；{@link #isExpired(Instant)} 用「现在>=expiresAt」判过期。</li>
 * </ul>
 * 不可变值对象：每次回写/续期/计数都新建一份替换（避免共享状态被并发改坏）。</p>
 *
 * @param channelId 渠道 id（>=1）
 * @param hitCount  命中次数（>=0）
 * @param lastHitAt 最近命中时刻（非空）
 * @param expiresAt 过期时刻（非空，必须晚于 lastHitAt 或与之相等）
 */
public record AffinityCacheEntry(long channelId, long hitCount, Instant lastHitAt, Instant expiresAt) {

    /**
     * 紧凑构造器：校验不变量。
     *
     * @throws InvalidAffinityParameterException 字段非法
     */
    public AffinityCacheEntry {
        if (channelId < 1) {
            throw new InvalidAffinityParameterException("channel_id must be >= 1");
        }
        if (hitCount < 0) {
            throw new InvalidAffinityParameterException("hit_count must be >= 0");
        }
        if (lastHitAt == null) {
            throw new InvalidAffinityParameterException("last_hit_at is required");
        }
        if (expiresAt == null) {
            throw new InvalidAffinityParameterException("expires_at is required");
        }
    }

    /**
     * 创建初次命中条目（F-2029，PRD CH-4 §3 节点 af_write）。
     *
     * @param channelId 命中后回写的渠道 id
     * @param now       当前时刻（注入便于单测）
     * @param ttlSec    TTL 秒（>=1，由 {@link com.nexa.routing.domain.model.AffinityRule#effectiveTtlSeconds} 计算）
     * @return 新条目（hitCount=1）
     */
    public static AffinityCacheEntry firstHit(long channelId, Instant now, long ttlSec) {
        if (ttlSec < 1) {
            throw new InvalidAffinityParameterException("ttl_seconds must be >= 1");
        }
        Instant n = now == null ? Instant.now() : now;
        return new AffinityCacheEntry(channelId, 1L, n, n.plusSeconds(ttlSec));
    }

    /**
     * 续期一次命中（F-2029 / F-2031，PRD CH-4 §3 节点 af_write 「成功才回写/续期」）。
     *
     * <p>领域规则：复用同一条目时 hitCount+1，刷新 lastHitAt 与 expiresAt。{@code channelId} 保持不变
     * （亲和命中的本质就是粘在同一渠道）；如需切到新渠道由调用方新建条目替换（switchOnSuccess 失败的失效场景）。</p>
     *
     * @param now    当前时刻
     * @param ttlSec 续期 TTL 秒
     * @return 续期后的新条目
     */
    public AffinityCacheEntry renew(Instant now, long ttlSec) {
        if (ttlSec < 1) {
            throw new InvalidAffinityParameterException("ttl_seconds must be >= 1");
        }
        Instant n = now == null ? Instant.now() : now;
        return new AffinityCacheEntry(channelId, hitCount + 1, n, n.plusSeconds(ttlSec));
    }

    /**
     * 判断本条目是否过期（PRD CH-4 §3 「缓存命中且未过期才复用」）。
     *
     * @param now 当前时刻（注入便于单测）
     * @return 现在 >= expiresAt 即过期
     */
    public boolean isExpired(Instant now) {
        Instant n = now == null ? Instant.now() : now;
        return !n.isBefore(expiresAt);
    }
}
