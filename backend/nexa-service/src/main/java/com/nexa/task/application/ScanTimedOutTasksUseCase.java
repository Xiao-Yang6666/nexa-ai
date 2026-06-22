package com.nexa.task.application;

import com.nexa.task.application.port.RefundPort;
import com.nexa.task.domain.model.Task;
import com.nexa.task.domain.repository.TaskRepository;
import com.nexa.task.domain.vo.RefundResult;
import com.nexa.task.domain.vo.TaskStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 超时任务扫描用例（应用层，F-2011 GetTimedOutUnfinishedTasks + AT-3 超时处理）。
 *
 * <p>用例编排：按 cutoff 扫描超时候选（仓储 {@link TaskRepository#findTimedOut}）→ 逐个经 CAS
 * 标记 FAILURE（超时）→ CAS 成功者触发全额退款（PRD AT-3/AT-4）。<b>CAS 守卫</b>避免覆盖在扫描与
 * 标记之间已自然完成的任务（更新失败=已被改写，跳过，不退款）。</p>
 *
 * <p>由后台定时任务（cron/scheduler，本 wave 不含调度器装配，仅提供可调用用例）周期触发。</p>
 */
@Service
public class ScanTimedOutTasksUseCase {

    /** 单批扫描上限（防一次处理过多，PRD AT-3 批量超时处理）。 */
    private static final int DEFAULT_BATCH_LIMIT = 200;

    private final TaskRepository taskRepository;
    private final RefundPort refundPort;

    /**
     * @param taskRepository 任务仓储
     * @param refundPort     退款落账端口
     */
    public ScanTimedOutTasksUseCase(TaskRepository taskRepository, RefundPort refundPort) {
        this.taskRepository = taskRepository;
        this.refundPort = refundPort;
    }

    /**
     * 扫描并处理超时任务（F-2011）。
     *
     * @param cutoffEpochSec 截止时间（submit_time 早于此且未完成视为超时）
     * @return 实际标记超时（CAS 成功）的任务数
     */
    public int scanAndExpire(long cutoffEpochSec) {
        List<Task> candidates = taskRepository.findTimedOut(cutoffEpochSec, DEFAULT_BATCH_LIMIT);
        long now = Instant.now().getEpochSecond();
        int expired = 0;
        for (Task task : candidates) {
            TaskStatus from = task.status();
            // 二次确认超时（聚合判定，防仓储查询与处理间状态变化）。
            if (!task.isTimedOut(cutoffEpochSec)) {
                continue;
            }
            task.markFailure("task timed out (cutoff=" + cutoffEpochSec + ")", now);
            // CAS 守卫：仅当库中仍为 from 状态才标记超时，避免覆盖已自然完成的任务（PRD AT-3）。
            boolean won = taskRepository.updateWithStatus(task, from);
            if (!won) {
                // 被他进程改写（已自然完成）→ 跳过不退款（CAS 保护态，PRD AT-3）。
                continue;
            }
            expired++;
            // 超时全额退款（失败终态，PRD AT-4），按 billing_source 分流落账。
            RefundResult result = task.settleRefund(0);
            if (result.type() != RefundResult.Type.SKIP && result.refundQuota() > 0
                    && task.billingContext() != null && task.userId() != null) {
                refundPort.refund(task.userId(), task.billingContext(), result);
            }
        }
        return expired;
    }
}
