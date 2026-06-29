package com.nexa.domain.task.exception;

import com.nexa.common.kernel.HttpAwareDomainException;

/**
 * 任务并发冲突异常（F-2002 CAS 失败，RowsAffected=0 → 重试或放弃）。
 *
 * <p>PRD AT-1 §CAS 守卫：并发调用 {@code UpdateWithStatus(fromStatus, ...)} 只有一个赢得更新
 * （RowsAffected>0），其余 RowsAffected=0 视为被他进程改写，应用层据此重试或放弃本次更新。
 * 本异常不映射 HTTP 状态码（内部协调用），应用层 catch 后决策重试逻辑。</p>
 */
public class TaskConcurrencyException extends HttpAwareDomainException {

    public TaskConcurrencyException(String message) {
        // 409 Conflict（内部用，不暴露给 API）
        super("TASK_CAS_CONFLICT", 409, message);
    }

    /**
     * CAS 更新失败（被他进程改写）。
     *
     * @param taskId     任务 ID
     * @param fromStatus 预期状态
     * @return 异常
     */
    public static TaskConcurrencyException casConflict(String taskId, String fromStatus) {
        return new TaskConcurrencyException(
                "CAS conflict when updating task " + taskId + " from status " + fromStatus);
    }
}
