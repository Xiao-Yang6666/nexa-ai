package com.nexa.routing.domain.vo;

import com.nexa.routing.domain.model.AffinityRule;

import java.util.Map;
import java.util.Optional;

/**
 * 亲和解析结果值对象（不可变，F-2029/F-2030/F-2034，PRD CH-4 选渠前判定产物）。
 *
 * <p>领域规则来源：PRD CH-4 §3。{@code AffinityResolver.resolve} 对一个请求做亲和判定后产出本结果，
 * 供选渠/relay 主干消费：
 * <ul>
 *   <li>{@link #matchedRule} —— 命中的规则（未命中任何规则为空，主干走 CH-2 普通选渠）。</li>
 *   <li>{@link #cacheKey} —— 命中规则下提取出的缓存键（键提取失败为空，回退普通选渠）。</li>
 *   <li>{@link #stickyChannelId} —— 缓存命中且未过期时的粘连渠道 id（直接复用，PRD CH-4 §3 af_stick）。</li>
 *   <li>{@link #passHeaders} —— F-2030 命中规则的 header 透传模板（注入上游；未命中为空 Map）。</li>
 *   <li>{@link #skipRetryOnFailure} —— F-2034 命中失败是否跳重试（未命中=false 走正常重试）。</li>
 * </ul>
 * 四种主干分支由本结果的字段组合表达，调用方无需再读规则细节（充血结果，封装判定语义）。</p>
 *
 * @param matchedRule        命中的规则（可空）
 * @param cacheKey           提取出的缓存键（可空）
 * @param stickyChannelId    粘连渠道 id（缓存命中时存在，否则空）
 * @param passHeaders        透传 header 模板（非空 Map）
 * @param skipRetryOnFailure 命中失败是否跳重试
 */
public record AffinityDecision(
        AffinityRule matchedRule,
        AffinityCacheKey cacheKey,
        Long stickyChannelId,
        Map<String, String> passHeaders,
        boolean skipRetryOnFailure) {

    /** 紧凑构造器：passHeaders 归一为非空不可变 Map。 */
    public AffinityDecision {
        passHeaders = passHeaders == null ? Map.of() : Map.copyOf(passHeaders);
    }

    /**
     * 未命中任何亲和规则的结果（PRD CH-4 §3 节点 af_normal：直走普通选渠）。
     *
     * @return 无亲和结果（无规则、无键、无粘连、无透传、不跳重试）
     */
    public static AffinityDecision noAffinity() {
        return new AffinityDecision(null, null, null, Map.of(), false);
    }

    /**
     * 命中规则但无粘连（键提取失败或缓存未命中/过期）——回退普通选渠但保留 header 透传与跳重试语义。
     *
     * @param rule     命中规则
     * @param cacheKey 缓存键（键提取成功则非空，提取失败可空）
     * @return 命中无粘连结果
     */
    public static AffinityDecision matchedNoStick(AffinityRule rule, AffinityCacheKey cacheKey) {
        return new AffinityDecision(rule, cacheKey, null, rule.passHeaders(), rule.skipRetryOnFailure());
    }

    /**
     * 命中规则且缓存粘连命中（PRD CH-4 §3 节点 af_stick：复用上次成功渠道）。
     *
     * @param rule           命中规则
     * @param cacheKey       缓存键
     * @param stickyChannelId 粘连渠道 id
     * @return 命中粘连结果
     */
    public static AffinityDecision sticky(AffinityRule rule, AffinityCacheKey cacheKey, long stickyChannelId) {
        return new AffinityDecision(rule, cacheKey, stickyChannelId, rule.passHeaders(), rule.skipRetryOnFailure());
    }

    /** @return 是否命中了某条亲和规则 */
    public boolean hasMatch() {
        return matchedRule != null;
    }

    /** @return 是否有可复用的粘连渠道 */
    public boolean hasStickyChannel() {
        return stickyChannelId != null;
    }

    /** @return 粘连渠道 id（若有） */
    public Optional<Long> stickyChannel() {
        return Optional.ofNullable(stickyChannelId);
    }

    /** @return 缓存键（若有，用于成功后回写） */
    public Optional<AffinityCacheKey> cacheKeyOpt() {
        return Optional.ofNullable(cacheKey);
    }
}
