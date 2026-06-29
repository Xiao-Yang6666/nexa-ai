package com.nexa.application.routing;

import com.nexa.domain.routing.vo.AffinityDecision;
import com.nexa.domain.routing.vo.AutoGroupRetryContext;

import java.util.Map;
import java.util.Optional;

/**
 * 选渠路由决策结果（应用层产物，整合 F-2029~F-2037 选渠中间件的一次推进结果，PRD CH-4 + CH-5）。
 *
 * <p>领域规则来源：PRD CH-4（亲和缓存）+ CH-5（auto 分组跨组重试）。
 * {@link ResolveChannelRouteUseCase} 在 relay 转发前/重试循环每一步调用后产出本结果，供 relay 主干消费：
 * <ul>
 *   <li>{@link #channelId} —— 本步选中的渠道 id（空=本步无可用渠道，配合 {@link #exhausted} 判断是粘连缺失
 *       还是全组耗尽）。</li>
 *   <li>{@link #stickyHit} —— 是否来自亲和缓存粘连命中（F-2029，PRD CH-4 §3 af_stick）；true 时
 *       channelId 来自缓存复用，跳过 CH-5 选渠抽签。</li>
 *   <li>{@link #passHeaders} —— F-2030 命中规则向上游透传的 CLI 专属 header 模板（relay 注入上游请求）。</li>
 *   <li>{@link #skipRetryOnFailure} —— F-2034 命中规则失败时是否跳过跨渠道重试（true=直接返错保会话稳定）。</li>
 *   <li>{@link #exhausted} —— F-2035 auto 全组耗尽标志（true=终止重试循环，上抛「无可用渠道」）。</li>
 *   <li>{@link #affinityDecision} —— 亲和判定原始结果（relay 请求成功后回传
 *       {@link ResolveChannelRouteUseCase#onSuccess} 用于回写缓存 F-2031）。</li>
 *   <li>{@link #retryContext} —— 推进后的跨组重试上下文（F-2035/2036/2037；relay 重试循环下一步回传本值
 *       续推；null 表示非 auto 分组或已终止）。</li>
 * </ul>
 * 不可变值对象——每一步推进新建一份返回，relay 重试循环各步互不污染。</p>
 *
 * @param channelId          本步选中渠道 id（空=本步无渠道）
 * @param stickyHit          是否亲和缓存粘连命中（F-2029）
 * @param passHeaders        F-2030 透传 header 模板（非空 Map）
 * @param skipRetryOnFailure F-2034 命中失败是否跳重试
 * @param exhausted          F-2035 auto 全组耗尽（终止重试）
 * @param affinityDecision   亲和判定原始结果（成功回写用，可空）
 * @param retryContext       推进后的跨组重试上下文（可空）
 */
public record ChannelRouteDecision(
        Long channelId,
        boolean stickyHit,
        Map<String, String> passHeaders,
        boolean skipRetryOnFailure,
        boolean exhausted,
        AffinityDecision affinityDecision,
        AutoGroupRetryContext retryContext) {

    /** 紧凑构造器：passHeaders 归一为非空不可变 Map。 */
    public ChannelRouteDecision {
        passHeaders = passHeaders == null ? Map.of() : Map.copyOf(passHeaders);
    }

    /**
     * 构造「亲和粘连命中」结果（PRD CH-4 §3 af_stick：直接复用上次成功渠道，不再 CH-5 抽签）。
     *
     * @param channelId        粘连渠道 id
     * @param affinityDecision 亲和判定（携带 passHeaders/skipRetry/cacheKey，供成功后回写）
     * @return 粘连命中结果
     */
    public static ChannelRouteDecision stickyHit(long channelId, AffinityDecision affinityDecision) {
        return new ChannelRouteDecision(channelId, true, affinityDecision.passHeaders(),
                affinityDecision.skipRetryOnFailure(), false, affinityDecision, null);
    }

    /**
     * 构造「CH-5 选渠选到渠道」结果（普通选渠或回退选渠选中，PRD CH-5 §3 ag_use）。
     *
     * @param channelId        选中渠道 id
     * @param affinityDecision 亲和判定（命中规则时携带透传/跳重试/cacheKey，未命中为 noAffinity）
     * @param retryContext     推进后的跨组重试上下文（auto 分组时非空）
     * @return 选中结果
     */
    public static ChannelRouteDecision selected(long channelId, AffinityDecision affinityDecision,
                                                AutoGroupRetryContext retryContext) {
        return new ChannelRouteDecision(channelId, false, affinityDecision.passHeaders(),
                affinityDecision.skipRetryOnFailure(), false, affinityDecision, retryContext);
    }

    /**
     * 构造「本步无渠道但还可继续重试」结果（F-2036 CrossGroupRetry：本组耗尽，下一步切组重试）。
     *
     * @param affinityDecision 亲和判定
     * @param nextContext      下一步重试上下文（已切到下一组）
     * @return 无渠道但可续推结果
     */
    public static ChannelRouteDecision noChannelRetryable(AffinityDecision affinityDecision,
                                                          AutoGroupRetryContext nextContext) {
        return new ChannelRouteDecision(null, false, affinityDecision.passHeaders(),
                affinityDecision.skipRetryOnFailure(), false, affinityDecision, nextContext);
    }

    /**
     * 构造「全组耗尽，终止重试」结果（F-2035，PRD CH-5 §3 ag_nomore：上抛无可用渠道）。
     *
     * @param affinityDecision 亲和判定
     * @return 耗尽结果
     */
    public static ChannelRouteDecision exhausted(AffinityDecision affinityDecision) {
        return new ChannelRouteDecision(null, false, affinityDecision.passHeaders(),
                affinityDecision.skipRetryOnFailure(), true, affinityDecision, null);
    }

    /** @return 本步是否选到了可用渠道 */
    public boolean hasChannel() {
        return channelId != null;
    }

    /** @return 选中渠道 id（若有） */
    public Optional<Long> channel() {
        return Optional.ofNullable(channelId);
    }

    /** @return 推进后的重试上下文（若有，供 relay 重试循环下一步续推） */
    public Optional<AutoGroupRetryContext> retryContextOpt() {
        return Optional.ofNullable(retryContext);
    }
}
