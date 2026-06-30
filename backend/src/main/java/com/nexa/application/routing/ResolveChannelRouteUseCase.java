package com.nexa.application.routing;

import com.nexa.application.routing.port.ChannelSelectionPort;
import com.nexa.domain.routing.model.AffinityRule;
import com.nexa.domain.routing.repository.AffinityRuleRepository;
import com.nexa.domain.routing.service.AffinityResolver;
import com.nexa.domain.routing.service.CrossGroupRetryScheduler;
import com.nexa.domain.routing.vo.AffinityDecision;
import com.nexa.domain.routing.vo.AffinityRequestContext;
import com.nexa.domain.routing.vo.AffinitySettings;
import com.nexa.domain.routing.vo.AutoGroupRetryContext;
import com.nexa.domain.routing.vo.ChannelCandidate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import com.nexa.application.routing.result.ChannelRouteDecision;

/**
 * 选渠路由编排用例（W2 选渠中间件应用层入口，整合 F-2029~F-2037，PRD CH-4 + CH-5）。
 *
 * <p>领域规则来源：PRD CH-4（会话亲和缓存）+ CH-5（auto 分组跨组重试）+ FC-068/FC-072。
 * 本用例是 relay 转发主干在「选哪个渠道」处调用的<b>唯一应用层入口</b>，把两个领域服务
 * （{@link AffinityResolver} 亲和判定 + {@link CrossGroupRetryScheduler} 跨组重试调度）串成一条选渠流水线，
 * 自身只做薄编排（backend-engineer §2.1 应用层薄，业务规则全在 domain）：</p>
 *
 * <ol>
 *   <li><b>首次选渠 {@link #resolveFirst}</b>：先做亲和判定（CH-4）——命中规则且缓存粘连命中则直接复用粘连渠道
 *       （F-2029 af_stick，跳过 CH-5 抽签）；否则进 CH-5 选渠（普通分组直选；auto 分组走跨组重试调度首步）。</li>
 *   <li><b>重试推进 {@link #resolveRetry}</b>：relay 上游失败需重试时调本方法，按 F-2034 SkipRetryOnFailure 先判是否允许重试，
 *       再用上一步的重试上下文推进 CH-5 调度（组内降级 / 切组 / 全组耗尽）。</li>
 *   <li><b>成功回写 {@link #onSuccess}</b>：relay 请求成功后回传亲和判定，按 F-2031 switchOnSuccess 回写/续期
 *       「会话键→实际成功渠道」缓存。</li>
 * </ol>
 *
 * <p>注：本用例跨 bounded context（routing 自身 + channel 选渠实现）做编排，故放应用层而非 domain；
 * 它依赖 {@link ChannelSelectionPort}（CH-2 选渠委托端口）和 domain 端口（仓储），不直接碰 DB/框架细节。</p>
 */
@Service
public class ResolveChannelRouteUseCase {

    private final AffinityRuleRepository ruleRepository;
    private final AffinityResolver affinityResolver;
    private final CrossGroupRetryScheduler retryScheduler;
    private final ChannelSelectionPort channelSelectionPort;

    /**
     * @param ruleRepository       亲和规则/策略仓储（domain 端口，读启用规则 + settings）
     * @param affinityResolver     亲和解析领域服务（CH-4）
     * @param retryScheduler       跨组重试调度领域服务（CH-5）
     * @param channelSelectionPort CH-2 选渠委托端口（channel 上下文实现）
     */
    public ResolveChannelRouteUseCase(AffinityRuleRepository ruleRepository,
                                      AffinityResolver affinityResolver,
                                      CrossGroupRetryScheduler retryScheduler,
                                      ChannelSelectionPort channelSelectionPort) {
        this.ruleRepository = ruleRepository;
        this.affinityResolver = affinityResolver;
        this.retryScheduler = retryScheduler;
        this.channelSelectionPort = channelSelectionPort;
    }

    /**
     * 首次选渠（relay 转发前调用一次，F-2029/2030/2034/2035/2036/2037，PRD CH-4 §3 + CH-5 §3 起点）。
     *
     * <p>流程：
     * <ol>
     *   <li>读启用规则 + settings，做亲和判定（CH-4）。命中且缓存粘连命中 → 直接复用粘连渠道
     *       （{@link ChannelRouteDecision#stickyHit}，不进 CH-5）。</li>
     *   <li>无粘连：构造初始重试上下文（priorityRetry=0、当前组索引=0），进 CH-5 选渠首步：
     *       <ul>
     *         <li>普通（非 auto）分组：在 {@code group} 上直选一个满足渠道（priorityRetry=0）。</li>
     *         <li>auto 分组：委托 {@link CrossGroupRetryScheduler} 在 autoGroups 上逐组逐层选渠。</li>
     *       </ul>
     *   </li>
     * </ol>
     * 命中规则的 passHeaders（F-2030）与 skipRetryOnFailure（F-2034）随结果带回，无论是否粘连。</p>
     *
     * @param model           请求模型名
     * @param path            请求 path（亲和规则 path 匹配用）
     * @param group           token 使用分组（普通分组名，或字面量 {@code "auto"}）
     * @param autoGroups      auto 分组有序列表（F-2035；group=auto 时非空，否则可空）
     * @param crossGroupRetry 令牌级跨组重试开关（F-2036）
     * @param retryTimes      全局重试次数上限（F-2037）
     * @param request         请求上下文（提取会话键用）
     * @param now             当前时刻（注入便于单测）
     * @return 首步路由决策
     */
    public ChannelRouteDecision resolveFirst(String model, String path, String group,
                                             List<String> autoGroups, boolean crossGroupRetry,
                                             int retryTimes, AffinityRequestContext request, Instant now) {
        AffinitySettings settings = ruleRepository.loadSettings();
        List<AffinityRule> enabledRules = ruleRepository.findEnabledRules();

        // CH-4 亲和判定：命中且粘连命中则直接复用粘连渠道。
        AffinityDecision decision = affinityResolver.resolve(model, path, group, request, enabledRules, settings, now);
        if (decision.hasStickyChannel()) {
            // F-2029 af_stick：缓存命中未过期，复用上次成功渠道，跳过 CH-5 抽签。
            return ChannelRouteDecision.stickyHit(decision.stickyChannelId(), decision);
        }

        // 无粘连：进 CH-5 选渠首步。
        boolean isAuto = isAutoGroup(group);
        if (!isAuto) {
            // 普通分组：直选（priorityRetry=0 最高优先级层）。无渠道即视为「无可用渠道」终止。
            ChannelCandidate c = channelSelectionPort.selectChannel(group, model, 0);
            if (c == null) {
                return ChannelRouteDecision.exhausted(decision);
            }
            // 普通分组无跨组上下文，retryContext 仅承载 priorityRetry 供重试降级用。
            AutoGroupRetryContext ctx = new AutoGroupRetryContext(0, 0, retryTimes, false, model);
            return ChannelRouteDecision.selected(c.channelId(), decision, ctx);
        }

        // auto 分组：委托 CH-5 跨组重试调度首步。
        AutoGroupRetryContext initial = AutoGroupRetryContext.initial(retryTimes, crossGroupRetry, model);
        return scheduleAuto(decision, initial, autoGroups);
    }

    /**
     * 重试推进（relay 上游失败需重试时调用，F-2034/2035/2036/2037，PRD CH-5 §3 重试循环）。
     *
     * <p>领域规则：
     * <ul>
     *   <li>F-2034 命中规则 skipRetryOnFailure=true → 不重试，直接返「耗尽」让 relay 上抛错误（保会话稳定，
     *       避免缓存被刷到别的渠道破坏粘连）。</li>
     *   <li>否则按上一步 retryContext 推进 CH-5 调度：普通分组组内优先级降级；auto 分组逐组逐层 + 切组。</li>
     * </ul>
     * relay 在一个重试循环里反复调本方法（传上一步带回的 retryContext），直到拿到渠道或 exhausted=true。</p>
     *
     * @param previous   上一步决策（携带亲和判定 + retryContext）
     * @param autoGroups auto 分组有序列表（auto 分组时非空）
     * @return 推进后的路由决策（选到渠道 / 可续推 / 耗尽）
     */
    public ChannelRouteDecision resolveRetry(ChannelRouteDecision previous, List<String> autoGroups) {
        AffinityDecision decision = previous.affinityDecision() == null
                ? AffinityDecision.noAffinity() : previous.affinityDecision();

        // F-2034：命中规则要求失败不跨渠道重试 → 直接终止，让 relay 返原始错误。
        if (decision.skipRetryOnFailure()) {
            return ChannelRouteDecision.exhausted(decision);
        }

        AutoGroupRetryContext ctx = previous.retryContext();
        if (ctx == null) {
            // 无重试上下文（如粘连命中后失败但允许重试）：无可续推状态，视为耗尽。
            return ChannelRouteDecision.exhausted(decision);
        }

        String group = previous.affinityDecision() != null && previous.affinityDecision().cacheKey() != null
                ? previous.affinityDecision().cacheKey().usingGroup() : null;
        boolean isAuto = isAutoGroup(group) || (autoGroups != null && !autoGroups.isEmpty());

        if (!isAuto) {
            // 普通分组重试：组内优先级降级一层后重选；超过 retryTimes 即耗尽。
            int nextPriority = ctx.priorityRetry() + 1;
            if (nextPriority > ctx.retryTimes()) {
                return ChannelRouteDecision.exhausted(decision);
            }
            ChannelCandidate c = channelSelectionPort.selectChannel(group, ctx.model(), nextPriority);
            if (c == null) {
                return ChannelRouteDecision.exhausted(decision);
            }
            AutoGroupRetryContext next = new AutoGroupRetryContext(0, nextPriority, ctx.retryTimes(), false, ctx.model());
            return ChannelRouteDecision.selected(c.channelId(), decision, next);
        }

        // auto 分组重试：推进 CH-5 调度（组内降级 → 切组 → 全组耗尽）。
        return scheduleAuto(decision, ctx, autoGroups);
    }

    /**
     * 请求成功后回写亲和缓存（F-2031，PRD CH-4 §3 af_switch→af_write）。
     *
     * <p>薄委托 {@link AffinityResolver#onSuccess}：仅当命中规则 + 有缓存键 + switchOnSuccess=true 时
     * 才把「会话键→实际成功渠道」回写/续期（switchOnSuccess=false 不回写）。</p>
     *
     * @param decision         首步/重试步带回的亲和判定
     * @param successChannelId 本次实际成功的渠道 id
     * @param now              当前时刻
     */
    public void onSuccess(AffinityDecision decision, long successChannelId, Instant now) {
        AffinitySettings settings = ruleRepository.loadSettings();
        affinityResolver.onSuccess(decision, successChannelId, settings, now);
    }

    /**
     * 推进 auto 分组 CH-5 调度并映射为路由决策（首步与重试步共用）。
     *
     * @param decision   亲和判定
     * @param ctx        当前重试上下文
     * @param autoGroups auto 分组有序列表
     * @return 路由决策
     */
    private ChannelRouteDecision scheduleAuto(AffinityDecision decision, AutoGroupRetryContext ctx,
                                              List<String> autoGroups) {
        CrossGroupRetryScheduler.AutoGroupRetryResult r =
                retryScheduler.schedule(ctx, autoGroups, channelSelectionPort::selectChannel);
        if (r.hasChannel()) {
            // ag_use：选到渠道。
            return ChannelRouteDecision.selected(r.selected().channelId(), decision, r.nextContext());
        }
        if (r.allExhausted()) {
            // ag_nomore：全组耗尽。
            return ChannelRouteDecision.exhausted(decision);
        }
        // F-2036 CrossGroupRetry：本次无渠道但已标记下次切组，relay 下一步用 nextContext 续推。
        return ChannelRouteDecision.noChannelRetryable(decision, r.nextContext());
    }

    /**
     * 判断是否 auto 分组（字面量 {@code "auto"}，大小写不敏感，F-2035 触发跨组重试调度）。
     *
     * @param group 分组名
     * @return true=auto 分组
     */
    private static boolean isAutoGroup(String group) {
        return group != null && "auto".equalsIgnoreCase(group.trim());
    }
}
