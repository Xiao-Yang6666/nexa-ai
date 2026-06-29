package com.nexa.domain.task.repository;

import com.nexa.domain.task.model.Task;
import com.nexa.domain.task.vo.TaskQuery;
import com.nexa.domain.task.vo.TaskStatus;

import java.util.List;
import java.util.Optional;

/**
 * 异步任务仓储接口（domain 定接口，infrastructure 实现，backend-engineer §2.3）。
 *
 * <p>核心方法：
 * <ul>
 *   <li>{@link #save} — 新建任务（InitTask，F-2001）。</li>
 *   <li>{@link #updateWithStatus} — CAS 条件更新（F-2002 UpdateWithStatus，以 fromStatus 为 WHERE 守卫）。</li>
 *   <li>{@link #findByTaskId} — 按 task_id 查（含 self-scope 校验的查询，F-2003/F-2006/F-2007）。</li>
 *   <li>{@link #findAll} — 分页列表（F-2003 用户侧 / F-2004 管理侧）。</li>
 *   <li>{@link #countAll} — 总数（配合 findAll 分页）。</li>
 *   <li>{@link #findTimedOut} — 扫描超时未完成任务（F-2011 GetTimedOutUnfinishedTasks）。</li>
 * </ul>
 * </p>
 */
public interface TaskRepository {

    /**
     * 新建任务（InitTask，F-2001）。
     *
     * @param task 任务聚合（未持久化）
     * @return 持久化后的任务（含 id）
     */
    Task save(Task task);

    /**
     * CAS 条件更新（F-2002 UpdateWithStatus，以 fromStatus 为 WHERE 守卫）。
     *
     * <p>PRD AT-1 §CAS：以 {@code fromStatus} 为 WHERE 守卫执行 UPDATE，仅当当前库中状态 = fromStatus
     * 时才更新成功（RowsAffected>0）；否则返回 false（被他进程改写，调用方放弃本次更新等下一轮）。
     * 禁用无守卫 bulk update（PRD AT-4 禁用）。</p>
     *
     * @param task       已调用 advanceTo/markSuccess/markFailure 的任务聚合
     * @param fromStatus 期望的 WHERE 守卫状态（= {@code task.statusBeforeAdvance()}）
     * @return true=赢得更新（RowsAffected>0），false=CAS 失败（被他进程改写）
     */
    boolean updateWithStatus(Task task, TaskStatus fromStatus);

    /**
     * 按 id（主键）查询任务。
     *
     * @param id 主键
     * @return 任务聚合（不存在返回 empty）
     */
    Optional<Task> findById(long id);

    /**
     * 按 task_id 查询任务（F-2003/F-2006/F-2007）。
     *
     * @param taskId 任务 ID
     * @return 任务聚合（不存在返回 empty）
     */
    Optional<Task> findByTaskId(String taskId);

    /**
     * 按 task_id + userId 查询（self-scope 归属验证，F-2003 用户侧隔离）。
     *
     * @param taskId 任务 ID
     * @param userId 归属用户 id
     * @return 任务聚合（不存在/不属于该用户返回 empty）
     */
    Optional<Task> findByTaskIdAndUserId(String taskId, int userId);

    /**
     * 分页列表（F-2003 用户侧 / F-2004 管理侧，按 query 条件过滤）。
     *
     * @param query 查询条件（含分页、用户隔离）
     * @return 当前页任务聚合列表
     */
    List<Task> findAll(TaskQuery query);

    /**
     * 总数（配合 {@link #findAll} 分页，F-2003/F-2004）。
     *
     * @param query 查询条件（分页参数忽略，count 用）
     * @return 满足条件的总条数
     */
    long countAll(TaskQuery query);

    /**
     * 扫描超时未完成任务（F-2011 GetTimedOutUnfinishedTasks）。
     *
     * <p>命中条件：{@code progress!='100%' AND status NOT IN(FAILURE,SUCCESS) AND submit_time<cutoff}
     * （PRD AT-3 §超时扫描）。返回的任务后续由应用层经 CAS 标记超时（转 FAILURE），CAS 守卫避免
     * 覆盖已自然完成的任务。</p>
     *
     * @param cutoffEpochSec 截止时间（epoch 秒）
     * @param limit          批次大小（防超大批次）
     * @return 超时候选任务列表
     */
    List<Task> findTimedOut(long cutoffEpochSec, int limit);
}
