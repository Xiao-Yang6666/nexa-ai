package com.nexa.routing.domain.service;

import com.nexa.routing.domain.exception.AutoGroupsNotEnabledException;
import com.nexa.routing.domain.vo.AutoGroupRetryContext;
import com.nexa.routing.domain.vo.ChannelCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CrossGroupRetryScheduler} 单测（纯 JUnit，零 Spring/DB）。
 *
 * <p>覆盖 PRD CH-5 全部状态机分支（F-2035 逐组耗尽 + F-2036 跨组开关 + F-2037 RetryTimes）：
 * 单组单层选到 / 组内降级 / 立即切组 / 延迟切组 / 全组耗尽 / autoGroups 空。</p>
 */
@DisplayName("CrossGroupRetryScheduler 跨组重试调度器")
class CrossGroupRetrySchedulerTest {

    private final CrossGroupRetryScheduler scheduler = new CrossGroupRetryScheduler();

    /** Lookup mock：按 (group, model, priorityRetry) 三元组返回预设候选（null=该组该层无渠道）。 */
    private static class StubLookup implements CrossGroupRetryScheduler.ChannelLookup {
        private final Map<String, ChannelCandidate> table = new HashMap<>();

        StubLookup put(String group, String model, int priorityRetry, ChannelCandidate c) {
            table.put(group + "|" + model + "|" + priorityRetry, c);
            return this;
        }

        @Override
        public ChannelCandidate selectChannel(String group, String model, int priorityRetry) {
            return table.get(group + "|" + model + "|" + priorityRetry);
        }
    }

    @Test
    @DisplayName("F-2035 当前组 0 层有渠道 → 直接选中")
    void selectFromCurrentGroupFirstLayer() {
        ChannelCandidate c = new ChannelCandidate(101L, "g1", 0, 1);
        StubLookup lookup = new StubLookup().put("g1", "gpt-4o", 0, c);
        AutoGroupRetryContext ctx = AutoGroupRetryContext.initial(3, false, "gpt-4o");

        var res = scheduler.schedule(ctx, List.of("g1", "g2"), lookup);

        assertTrue(res.hasChannel());
        assertEquals(101L, res.selected().channelId());
        assertEquals(0, res.nextContext().autoGroupIndex());
        assertEquals(0, res.nextContext().priorityRetry());
    }

    @Test
    @DisplayName("F-2035 0 层无、1 层有 → 组内降级到 priorityRetry=1 选中")
    void degradeWithinGroup() {
        ChannelCandidate c = new ChannelCandidate(102L, "g1", 1, 1);
        StubLookup lookup = new StubLookup().put("g1", "gpt-4o", 1, c); // 0 层缺
        AutoGroupRetryContext ctx = AutoGroupRetryContext.initial(3, false, "gpt-4o");

        var res = scheduler.schedule(ctx, List.of("g1"), lookup);

        assertTrue(res.hasChannel());
        assertEquals(102L, res.selected().channelId());
        assertEquals(1, res.nextContext().priorityRetry());
    }

    @Test
    @DisplayName("F-2035/F-2036=false 当前组耗尽 → 立即切下一组并 SetRetry(0)，本次仍取到下组渠道")
    void switchGroupImmediatelyWhenCrossOff() {
        ChannelCandidate c = new ChannelCandidate(201L, "g2", 0, 1);
        StubLookup lookup = new StubLookup().put("g2", "gpt-4o", 0, c); // g1 全空
        AutoGroupRetryContext ctx = AutoGroupRetryContext.initial(2, false, "gpt-4o");

        var res = scheduler.schedule(ctx, List.of("g1", "g2"), lookup);

        assertTrue(res.hasChannel());
        assertEquals(201L, res.selected().channelId());
        assertEquals(1, res.nextContext().autoGroupIndex()); // 已切到 g2
        assertEquals(0, res.nextContext().priorityRetry()); // 新组归零
    }

    @Test
    @DisplayName("F-2036=true 当前组耗尽 → 本次返回无渠道但下次切下一组（ResetRetryNextTry 语义）")
    void delayedSwitchWhenCrossOn() {
        StubLookup lookup = new StubLookup(); // g1 全空、g2 有但本次不该用到
        lookup.put("g2", "gpt-4o", 0, new ChannelCandidate(201L, "g2", 0, 1));
        AutoGroupRetryContext ctx = AutoGroupRetryContext.initial(2, true, "gpt-4o");

        var res = scheduler.schedule(ctx, List.of("g1", "g2"), lookup);

        assertFalse(res.hasChannel());
        assertFalse(res.allExhausted());
        // 本次返回无渠道，但 nextContext 已切到下一组（下次重试时调度才会看 g2）。
        assertEquals(1, res.nextContext().autoGroupIndex());
        assertEquals(0, res.nextContext().priorityRetry());
    }

    @Test
    @DisplayName("F-2037 全组所有层级都无渠道 → allExhausted=true（无可用渠道）")
    void allGroupsExhausted() {
        StubLookup lookup = new StubLookup(); // 全空
        AutoGroupRetryContext ctx = AutoGroupRetryContext.initial(2, false, "gpt-4o");

        var res = scheduler.schedule(ctx, List.of("g1", "g2"), lookup);

        assertFalse(res.hasChannel());
        assertTrue(res.allExhausted());
        assertNull(res.nextContext());
    }

    @Test
    @DisplayName("F-2035 autoGroups 为空 → 抛 AutoGroupsNotEnabledException")
    void autoGroupsEmptyThrows() {
        AutoGroupRetryContext ctx = AutoGroupRetryContext.initial(3, false, "gpt-4o");
        assertThrows(AutoGroupsNotEnabledException.class, () ->
                scheduler.schedule(ctx, List.of(), new StubLookup()));
        assertThrows(AutoGroupsNotEnabledException.class, () ->
                scheduler.schedule(ctx, null, new StubLookup()));
    }

    @Test
    @DisplayName("F-2037 RetryTimes=0 边界：组内不降级，无渠道立即切组")
    void retryTimesZeroBoundary() {
        ChannelCandidate c = new ChannelCandidate(301L, "g2", 0, 1);
        StubLookup lookup = new StubLookup().put("g2", "gpt-4o", 0, c); // g1 0 层空、g2 0 层有
        AutoGroupRetryContext ctx = AutoGroupRetryContext.initial(0, false, "gpt-4o");

        var res = scheduler.schedule(ctx, List.of("g1", "g2"), lookup);

        assertTrue(res.hasChannel());
        assertEquals(301L, res.selected().channelId());
        // priorityRetry=0 已等于 retryTimes=0，应直接切组。
        assertEquals(1, res.nextContext().autoGroupIndex());
    }
}
