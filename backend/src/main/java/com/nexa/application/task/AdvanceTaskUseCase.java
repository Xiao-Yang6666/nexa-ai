package com.nexa.application.task;

import com.nexa.domain.task.model.Task;
import com.nexa.domain.task.repository.TaskRepository;
import com.nexa.domain.task.vo.TaskStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 任务状态推进用例（应用层，F-2002 CAS 条件更新 + AT-1 轮询推进）。
 *
 * <p>用例编排：读任务 → 调聚合行为方法（{@link Task#advanceTo}/{@link Task#markSuccess}/
 * {@link Task#markFailure}）→ 经仓储 {@link TaskRepository#updateWithStatus} 做 CAS 守卫写入。
 * 后台轮询/回调（F-2005/F-2007/F-2008 各平台 fetch）按上游回报状态调用本用例推进。</p>
 *
 * <p><b>CAS 语义</b>（PRD AT-1/F-2002）：以聚合 {@link Task#statusBeforeAdvance} 为 WHERE 守卫，
 * {@code updateWithStatus} 返回 false（RowsAffected=0）表示被他进程改写，本次放弃（调用方等下一轮，
 * 不报错）。状态机非法转换由聚合抛 {@code InvalidTaskParameterException}（接口层 400）。</p>
 */
@Service
public class AdvanceTaskUseCase {

    private final TaskRepository taskRepository;

    /** @param taskRepository 任务仓储 */
    public AdvanceTaskUseCase(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * 推进到中间状态（SUBMITTED/QUEUED/IN_PROGRESS/UNKNOWN）。
     *
     * @param taskId   任务 ID
     * @param to       目标状态
     * @param progress 进度（可空）
     * @return true=CAS 赢得更新；false=被他进程改写（放弃）；任务不存在返回 false
     */
    public boolean advance(String taskId, TaskStatus to, String progress) {
        Optional<Task> found = taskRepository.findByTaskId(taskId);
        if (found.isEmpty()) {
            return false;
        }
        Task task = found.get();
        TaskStatus from = task.status();
        task.advanceTo(to, progress, Instant.now().getEpochSecond());
        return taskRepository.updateWithStatus(task, from);
    }

    /**
     * 推进到成功终态（F-2002 + AT-1 SUCCESS）。
     *
     * @param taskId       任务 ID
     * @param resultData   产物 data JSON（可空）
     * @param actualTokens 实际 token 消耗（差额结算用，可空）
     * @return CAS 是否赢得更新
     */
    public boolean markSuccess(String taskId, String resultData, Long actualTokens) {
        Optional<Task> found = taskRepository.findByTaskId(taskId);
        if (found.isEmpty()) {
            return false;
        }
        Task task = found.get();
        TaskStatus from = task.status();
        task.markSuccess(resultData, actualTokens, Instant.now().getEpochSecond());
        return taskRepository.updateWithStatus(task, from);
    }

    /**
     * 推进到失败终态（F-2002 + AT-1 FAILURE，触发退款由结算用例处理）。
     *
     * @param taskId     任务 ID
     * @param failReason 失败原因（可空）
     * @return CAS 是否赢得更新
     */
    public boolean markFailure(String taskId, String failReason) {
        Optional<Task> found = taskRepository.findByTaskId(taskId);
        if (found.isEmpty()) {
            return false;
        }
        Task task = found.get();
        TaskStatus from = task.status();
        task.markFailure(failReason, Instant.now().getEpochSecond());
        return taskRepository.updateWithStatus(task, from);
    }
}
