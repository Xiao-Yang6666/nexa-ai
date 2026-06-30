package com.nexa.domain.task.exception;

import com.nexa.domain.kernel.HttpAwareDomainException;

/**
 * 任务不存在异常（F-2003/F-2004/F-2006/F-2007 查询未命中 → 404）。
 *
 * <p>应用层按 id/task_id 查不到任务时抛出，接口层翻译为 404 响应。</p>
 */
public class TaskNotFoundException extends HttpAwareDomainException {

    public TaskNotFoundException(String message) {
        super("TASK_NOT_FOUND", 404, message);
    }

    /**
     * 按 id 查不到任务。
     *
     * @param id 主键
     * @return 异常
     */
    public static TaskNotFoundException byId(long id) {
        return new TaskNotFoundException("task not found by id: " + id);
    }

    /**
     * 按 task_id 查不到任务。
     *
     * @param taskId 任务 ID
     * @return 异常
     */
    public static TaskNotFoundException byTaskId(String taskId) {
        return new TaskNotFoundException("task not found by task_id: " + taskId);
    }
}
