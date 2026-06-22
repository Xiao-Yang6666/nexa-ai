package com.nexa.task.domain.model;

import com.nexa.task.domain.exception.InvalidTaskParameterException;
import com.nexa.task.domain.vo.BillingContext;
import com.nexa.task.domain.vo.RefundResult;
import com.nexa.task.domain.vo.TaskPlatform;
import com.nexa.task.domain.vo.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Task} 任务聚合根单测（纯 JUnit，不起 Spring/DB）。
 *
 * <p>覆盖 PRD FL-asynctask AT-1~AT-4 领域规则：InitTask 校验、状态机合法/非法转换、终态不可逆、
 * 超时判定（F-2011）、退款差额结算分流（F-2009：按次跳过/成功差额/失败全退）、产物 URL 仅 SUCCESS。
 * 按正常/边界/异常三类组织（backend-engineer §3.3）。CAS（仓储职责）不在聚合单测范围。</p>
 */
@DisplayName("Task 任务聚合根")
class TaskTest {

    private static final long NOW = 1_700_000_000L;

    private Task newTask(BillingContext ctx) {
        return Task.initTask("up-task-1", TaskPlatform.MIDJOURNEY, 42, "default",
                7, 1000L, "IMAGINE", ctx, NOW);
    }

    @Nested
    @DisplayName("initTask 工厂")
    class InitTask {

        @Test
        @DisplayName("正常：以 NOT_START + progress=0% 落库")
        void initOk() {
            Task t = newTask(null);
            assertEquals(TaskStatus.NOT_START, t.status());
            assertEquals("0%", t.progress());
            assertEquals("up-task-1", t.taskId());
            assertEquals(42, t.userId());
            assertEquals(NOW, t.submitTime());
            assertNull(t.startTime());
        }

        @Test
        @DisplayName("异常：task_id 缺失抛 InvalidTaskParameterException")
        void initMissingTaskId() {
            assertThrows(InvalidTaskParameterException.class, () ->
                    Task.initTask("  ", TaskPlatform.SUNO, 1, null, null, 0, "MUSIC", null, NOW));
        }

        @Test
        @DisplayName("异常：action 缺失抛异常")
        void initMissingAction() {
            assertThrows(InvalidTaskParameterException.class, () ->
                    Task.initTask("x", TaskPlatform.SUNO, 1, null, null, 0, null, null, NOW));
        }
    }

    @Nested
    @DisplayName("状态机 advanceTo / 终态")
    class StateMachine {

        @Test
        @DisplayName("正常：NOT_START→IN_PROGRESS 记 startTime")
        void advanceToInProgress() {
            Task t = newTask(null);
            t.advanceTo(TaskStatus.IN_PROGRESS, "30%", NOW + 5);
            assertEquals(TaskStatus.IN_PROGRESS, t.status());
            assertEquals("30%", t.progress());
            assertEquals(NOW + 5, t.startTime());
            // statusBeforeAdvance 供仓储 CAS 守卫。
            assertEquals(TaskStatus.NOT_START, t.statusBeforeAdvance());
        }

        @Test
        @DisplayName("异常：advanceTo 不接受终态（须用 markSuccess/markFailure）")
        void advanceToTerminalRejected() {
            Task t = newTask(null);
            assertThrows(InvalidTaskParameterException.class, () ->
                    t.advanceTo(TaskStatus.SUCCESS, "100%", NOW));
        }

        @Test
        @DisplayName("边界：markSuccess 写 100%/finishTime，终态后不可再推进")
        void markSuccessThenLocked() {
            Task t = newTask(null);
            t.advanceTo(TaskStatus.IN_PROGRESS, "50%", NOW + 1);
            t.markSuccess("{\"url\":\"x\"}", 500L, NOW + 10);
            assertEquals(TaskStatus.SUCCESS, t.status());
            assertEquals("100%", t.progress());
            assertEquals(NOW + 10, t.finishTime());
            // 终态不可逆：再 advanceTo 任意态非法。
            assertFalse(t.canTransitionTo(TaskStatus.IN_PROGRESS));
            assertThrows(InvalidTaskParameterException.class, () ->
                    t.advanceTo(TaskStatus.QUEUED, null, NOW + 11));
        }

        @Test
        @DisplayName("异常：已 FAILURE 再 markSuccess 非法")
        void failureThenSuccessRejected() {
            Task t = newTask(null);
            t.markFailure("upstream error", NOW + 2);
            assertEquals(TaskStatus.FAILURE, t.status());
            assertThrows(InvalidTaskParameterException.class, () ->
                    t.markSuccess(null, null, NOW + 3));
        }
    }

    @Nested
    @DisplayName("isTimedOut 超时判定（F-2011）")
    class TimedOut {

        @Test
        @DisplayName("命中：未完成 + submit_time<cutoff")
        void timedOutHit() {
            Task t = newTask(null);
            t.advanceTo(TaskStatus.IN_PROGRESS, "20%", NOW + 1);
            // cutoff 晚于 submit_time(NOW)，progress!=100%，非终态 → 超时。
            assertTrue(t.isTimedOut(NOW + 100));
        }

        @Test
        @DisplayName("不命中：已 SUCCESS 终态不算超时")
        void successNotTimedOut() {
            Task t = newTask(null);
            t.markSuccess(null, null, NOW + 1);
            assertFalse(t.isTimedOut(NOW + 100));
        }

        @Test
        @DisplayName("不命中：submit_time>=cutoff")
        void newTaskNotTimedOut() {
            Task t = newTask(null);
            assertFalse(t.isTimedOut(NOW - 10));
        }
    }

    @Nested
    @DisplayName("settleRefund 退款差额结算（F-2009）")
    class Refund {

        @Test
        @DisplayName("按次计费：SKIP（跳过差额结算）")
        void perCallSkip() {
            BillingContext ctx = BillingContext.of(true, BillingContext.BillingSource.WALLET, 1000);
            Task t = newTask(ctx);
            t.markSuccess(null, 300L, NOW + 1);
            RefundResult r = t.settleRefund(300);
            assertEquals(RefundResult.Type.SKIP, r.type());
            assertEquals(0, r.refundQuota());
        }

        @Test
        @DisplayName("成功按量：差额结算多退（pre 1000 - actual 300 = 700）")
        void successDifferentialRefund() {
            BillingContext ctx = BillingContext.of(false, BillingContext.BillingSource.WALLET, 1000);
            Task t = newTask(ctx);
            t.markSuccess(null, 300L, NOW + 1);
            RefundResult r = t.settleRefund(300);
            assertEquals(RefundResult.Type.DIFFERENTIAL, r.type());
            assertEquals(700, r.refundQuota());
        }

        @Test
        @DisplayName("成功按量：实际>预扣，少不补（退 0）")
        void successNoTopUp() {
            BillingContext ctx = BillingContext.of(false, BillingContext.BillingSource.WALLET, 1000);
            Task t = newTask(ctx);
            t.markSuccess(null, 1500L, NOW + 1);
            RefundResult r = t.settleRefund(1500);
            assertEquals(RefundResult.Type.DIFFERENTIAL, r.type());
            assertEquals(0, r.refundQuota());
        }

        @Test
        @DisplayName("失败：全额退预扣")
        void failureFullRefund() {
            BillingContext ctx = BillingContext.of(false, BillingContext.BillingSource.SUBSCRIPTION, 1000);
            Task t = newTask(ctx);
            t.markFailure("error", NOW + 1);
            RefundResult r = t.settleRefund(0);
            assertEquals(RefundResult.Type.FULL_REFUND, r.type());
            assertEquals(1000, r.refundQuota());
        }

        @Test
        @DisplayName("异常：未到终态结算抛异常")
        void settleBeforeTerminal() {
            BillingContext ctx = BillingContext.of(false, BillingContext.BillingSource.WALLET, 1000);
            Task t = newTask(ctx);
            assertThrows(InvalidTaskParameterException.class, () -> t.settleRefund(0));
        }

        @Test
        @DisplayName("边界：无计费上下文 → SKIP")
        void noBillingContextSkip() {
            Task t = newTask(null);
            t.markFailure("error", NOW + 1);
            assertEquals(RefundResult.Type.SKIP, t.settleRefund(0).type());
        }
    }

    @Nested
    @DisplayName("resultUrl 产物访问")
    class ResultUrl {

        @Test
        @DisplayName("仅 SUCCESS 返回 data")
        void onlySuccess() {
            Task t = newTask(null);
            t.advanceTo(TaskStatus.IN_PROGRESS, "50%", NOW + 1);
            assertNull(t.resultUrl());
            t.markSuccess("{\"video\":\"u\"}", null, NOW + 2);
            assertEquals("{\"video\":\"u\"}", t.resultUrl());
        }
    }
}
