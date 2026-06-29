package com.nexa.domain.routing.service;

import com.nexa.domain.routing.exception.AutoGroupsNotEnabledException;
import com.nexa.domain.routing.vo.AutoGroupRetryContext;
import com.nexa.domain.routing.vo.ChannelCandidate;

import java.util.List;

/**
 * auto 分组跨组重试调度器（领域服务，F-2035 逐组耗尽 + F-2036 令牌级跨组开关 + F-2037 RetryTimes，PRD CH-5）。
 *
 * <p>领域规则来源：FC-072 {@code service/channel_select.go:83-162 CacheGetRandomSatisfiedChannel}。
 * 令牌使用 {@code Group=auto} 分组时，本调度器驱动「组内优先级降级 → 组耗尽切组 → 组用尽终止」的
 * 嵌套循环判定：
 * <ul>
 *   <li>当前组按 {@code priorityRetry} 选满足渠道（CH-2 优先级分层+权重随机，委托 {@code channelSelector}）。</li>
 *   <li>组内 {@code priorityRetry >= retryTimes}（F-2037）则本组耗尽——切下一组
 *       {@code AutoGroupIndex+1 + SetRetry(0)}（PRD CH-5 §3 ag_exhaust→ag_switch）。</li>
 *   <li>令牌 {@code CrossGroupRetry=true}（F-2036）时本次仍用当前组，下次才切组
 *       （{@code ResetRetryNextTry}，PRD CH-5 §3 ag_cross→ag_next）。</li>
 *   <li>{@code GetAutoGroups()} 为空 → 抛 {@link AutoGroupsNotEnabledException}（PRD CH-5 §3 ag_disable）。</li>
 *   <li>全组耗尽 → 返回 null（调用方上抛「无可用渠道」）。</li>
 * </ul>
 * 本调度器为领域服务——跨多个聚合的业务逻辑（Token 的分组/跨组开关 + Channel/Ability 候选集），
 * 不属于单个聚合方法（backend-engineer §2.4 领域服务）。零框架依赖，可纯单测。</p>
 *
 * <p>注：本调度器只负责「选哪个组、该不该切、给不给渠道」的<b>决策逻辑</b>，实际从 Ability 查询候选集 +
 * 按优先级权重抽签的操作通过 {@code channelSelector}（CH-2 实现）委托（解耦查询/抽签实现，
 * 可单测时用桩替换）。</p>
 */
public class CrossGroupRetryScheduler {

    /**
     * 为 auto 分组请求调度选择满足渠道（F-2035/2036，PRD CH-5 完整流程）。
     *
     * <p>调用方传入当前重试上下文（含已在第几组、当前 priorityRetry、是否开启跨组重试、
     * retryTimes 上限）。本方法推进一步后返回调度结果（选中渠道 or null + 新上下文）。
     * 调用方在重试循环里反复调本方法直到拿到渠道或全组耗尽。</p>
     *
     * @param ctx            当前重试上下文
     * @param autoGroups     有序 auto 分组列表（F-2035 GetAutoGroups，非空；空请调用方前置校验或抛）
     * @param channelLookup  委托：给定 (group, model, priorityRetry) 返回满足渠道候选（CH-2 选渠子集）
     * @return 调度结果（含选中渠道或 null + 推进后的新上下文）
     * @throws AutoGroupsNotEnabledException autoGroups 为空
     */
    public AutoGroupRetryResult schedule(AutoGroupRetryContext ctx, List<String> autoGroups,
                                         ChannelLookup channelLookup) {
        if (autoGroups == null || autoGroups.isEmpty()) {
            throw new AutoGroupsNotEnabledException();
        }

        int groupIndex = ctx.autoGroupIndex();
        int priorityRetry = ctx.priorityRetry();
        int retryTimes = ctx.retryTimes();

        // 遍历剩余组（从当前组开始）。
        while (groupIndex < autoGroups.size()) {
            String currentGroup = autoGroups.get(groupIndex);

            // 本组内尝试选渠（委托 CH-2 逻辑：给定 group + model + priorityRetry 层级选一个渠道）。
            ChannelCandidate selected = channelLookup.selectChannel(currentGroup, ctx.model(), priorityRetry);

            if (selected != null) {
                // 选到渠道 → 返回成功（PRD CH-5 §3 ag_found-是 → ag_use）。
                AutoGroupRetryContext newCtx = new AutoGroupRetryContext(
                        groupIndex, priorityRetry, retryTimes, ctx.crossGroupRetry(), ctx.model());
                return AutoGroupRetryResult.success(selected, newCtx);
            }

            // 当前 priorityRetry 层无渠道，判是否还能降级。
            if (priorityRetry < retryTimes) {
                // 组内降级（PRD CH-5 §3 ag_exhaust-否 → ag_inc）。
                priorityRetry++;
                continue;
            }

            // 本组耗尽（priorityRetry >= retryTimes，PRD CH-5 §3 ag_exhaust-是）。
            if (ctx.crossGroupRetry()) {
                // F-2036 CrossGroupRetry=true：本次仍用当前组（返回 null 但标记下次切组）。
                // "本次仍用当前组" —— 因为本次已在当前组全层级无渠道，实际效果=返回无渠道
                // + 标记下次 autoGroupIndex+1（ResetRetryNextTry 语义）。
                AutoGroupRetryContext nextCtx = new AutoGroupRetryContext(
                        groupIndex + 1, 0, retryTimes, ctx.crossGroupRetry(), ctx.model());
                return AutoGroupRetryResult.exhaustedWithNextContext(nextCtx);
            }

            // CrossGroupRetry=false：立即切组（PRD CH-5 §3 ag_cross-否 → ag_switch）。
            groupIndex++;
            priorityRetry = 0; // 新组归零（SetRetry(0)）。
        }

        // 全组耗尽（PRD CH-5 §3 ag_more-无 → ag_nomore）。
        return AutoGroupRetryResult.allGroupsExhausted();
    }

    /**
     * 渠道选择委托接口（domain 端口——CH-2 优先级分层+权重随机选渠逻辑的抽象，infra/application 实现）。
     *
     * <p>隔离实际 DB 查询/加权随机算法，让 CrossGroupRetryScheduler 可纯单测。</p>
     */
    @FunctionalInterface
    public interface ChannelLookup {

        /**
         * 在指定 group 下按优先级层级选择一个满足渠道。
         *
         * @param group         分组名
         * @param model         请求模型
         * @param priorityRetry 当前优先级重试层级
         * @return 选中的候选渠道，无满足渠道返回 null
         */
        ChannelCandidate selectChannel(String group, String model, int priorityRetry);
    }

    /**
     * auto 分组调度结果。
     *
     * @param selected        选中渠道（null=本次无渠道）
     * @param nextContext     推进后的重试上下文（供重试循环下次调用用）
     * @param allExhausted    全组耗尽标志（true=终止重试循环，上抛无可用渠道）
     */
    public record AutoGroupRetryResult(ChannelCandidate selected, AutoGroupRetryContext nextContext,
                                       boolean allExhausted) {

        static AutoGroupRetryResult success(ChannelCandidate c, AutoGroupRetryContext ctx) {
            return new AutoGroupRetryResult(c, ctx, false);
        }

        static AutoGroupRetryResult exhaustedWithNextContext(AutoGroupRetryContext ctx) {
            return new AutoGroupRetryResult(null, ctx, false);
        }

        static AutoGroupRetryResult allGroupsExhausted() {
            return new AutoGroupRetryResult(null, null, true);
        }

        /** @return 是否选到了可用渠道 */
        public boolean hasChannel() {
            return selected != null;
        }
    }
}
