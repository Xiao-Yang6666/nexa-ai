package com.nexa.infrastructure.task.persistence;

import com.nexa.infrastructure.task.persistence.po.TaskPO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA 仓库（异步任务，基础设施层内部接口）。
 *
 * <p>仅供 {@link TaskRepositoryImpl} 内部使用，不直接暴露给应用/领域层——领域只认
 * {@code domain.repository.TaskRepository}。F-2002 CAS 用 {@link #updateWithStatus} 的
 * {@code @Modifying UPDATE ... WHERE status=:fromStatus}（乐观锁守卫，RowsAffected>0 才算赢）。
 * F-2003/F-2004 动态过滤用 JPQL 的 {@code (:param IS NULL OR ...)} 惯用法（null 参数跳过该维度）。</p>
 */
interface SpringDataTaskJpaRepository extends JpaRepository<TaskPO, Long> {

    /**
     * 按 task_id 查询（F-2006/F-2007）。
     *
     * @param taskId 任务 ID
     * @return 命中实体（可空）
     */
    Optional<TaskPO> findByTaskId(String taskId);

    /**
     * 按 task_id + user_id 查询（self-scope 归属验证，F-2003）。
     *
     * @param taskId 任务 ID
     * @param userId 归属用户 id
     * @return 命中实体（可空）
     */
    Optional<TaskPO> findByTaskIdAndUserId(String taskId, Integer userId);

    /**
     * CAS 条件更新（F-2002 UpdateWithStatus）。
     *
     * <p>PRD AT-1 §CAS：以 {@code fromStatus} 为 WHERE 守卫 UPDATE，仅当库中当前状态 = fromStatus
     * 时才更新（返回受影响行数 1）；被他进程改写时当前状态≠fromStatus → 受影响 0（CAS 失败）。
     * 一次性写全部可变字段（status/failReason/start/finish/progress/data/updatedAt），避免多次往返。</p>
     *
     * @param id         主键
     * @param fromStatus WHERE 守卫的预期状态
     * @param toStatus   目标状态
     * @param failReason 失败原因
     * @param startTime  开始时间
     * @param finishTime 完成时间
     * @param progress   进度
     * @param data       产物 JSON
     * @param updatedAt  更新时间
     * @return 受影响行数（1=赢得更新，0=CAS 失败）
     */
    @Modifying
    @Query("""
            UPDATE TaskPO t SET
                t.status = :toStatus,
                t.failReason = :failReason,
                t.startTime = :startTime,
                t.finishTime = :finishTime,
                t.progress = :progress,
                t.data = :data,
                t.updatedAt = :updatedAt
            WHERE t.id = :id AND t.status = :fromStatus
            """)
    int updateWithStatus(@Param("id") long id,
                         @Param("fromStatus") String fromStatus,
                         @Param("toStatus") String toStatus,
                         @Param("failReason") String failReason,
                         @Param("startTime") Long startTime,
                         @Param("finishTime") Long finishTime,
                         @Param("progress") String progress,
                         @Param("data") String data,
                         @Param("updatedAt") Long updatedAt);

    /**
     * 动态过滤分页列表（F-2003 用户侧 / F-2004 管理侧）。
     *
     * <p>{@code (:param IS NULL OR t.field = :param)} 惯用法：参数为 null 跳过该过滤维度。用户侧
     * 调用方传非空 userId 强制 self-scope（PRD AT-2）；管理侧 userId 可空 = 跨用户。按 id 降序（新在前）。</p>
     *
     * @param taskId    任务 ID（可空）
     * @param userId    用户 id（可空，用户侧必传）
     * @param channelId 渠道 id（可空）
     * @param platform  平台（可空）
     * @param action    动作（可空）
     * @param status    状态（可空）
     * @param startTime 提交时间起（可空）
     * @param endTime   提交时间止（可空）
     * @param pageable  分页
     * @return 当前页实体
     */
    @Query("""
            SELECT t FROM TaskPO t
            WHERE (:taskId IS NULL OR t.taskId = :taskId)
              AND (:userId IS NULL OR t.userId = :userId)
              AND (:channelId IS NULL OR t.channelId = :channelId)
              AND (:platform IS NULL OR t.platform = :platform)
              AND (:action IS NULL OR t.action = :action)
              AND (:status IS NULL OR t.status = :status)
              AND (:startTime IS NULL OR t.submitTime >= :startTime)
              AND (:endTime IS NULL OR t.submitTime <= :endTime)
            ORDER BY t.id DESC
            """)
    List<TaskPO> findPage(@Param("taskId") String taskId,
                                 @Param("userId") Integer userId,
                                 @Param("channelId") Integer channelId,
                                 @Param("platform") String platform,
                                 @Param("action") String action,
                                 @Param("status") String status,
                                 @Param("startTime") Long startTime,
                                 @Param("endTime") Long endTime,
                                 Pageable pageable);

    /**
     * 动态过滤计数（F-2003/F-2004 total）。
     *
     * @param taskId    任务 ID（可空）
     * @param userId    用户 id（可空）
     * @param channelId 渠道 id（可空）
     * @param platform  平台（可空）
     * @param action    动作（可空）
     * @param status    状态（可空）
     * @param startTime 提交时间起（可空）
     * @param endTime   提交时间止（可空）
     * @return 总数
     */
    @Query("""
            SELECT COUNT(t) FROM TaskPO t
            WHERE (:taskId IS NULL OR t.taskId = :taskId)
              AND (:userId IS NULL OR t.userId = :userId)
              AND (:channelId IS NULL OR t.channelId = :channelId)
              AND (:platform IS NULL OR t.platform = :platform)
              AND (:action IS NULL OR t.action = :action)
              AND (:status IS NULL OR t.status = :status)
              AND (:startTime IS NULL OR t.submitTime >= :startTime)
              AND (:endTime IS NULL OR t.submitTime <= :endTime)
            """)
    long countPage(@Param("taskId") String taskId,
                   @Param("userId") Integer userId,
                   @Param("channelId") Integer channelId,
                   @Param("platform") String platform,
                   @Param("action") String action,
                   @Param("status") String status,
                   @Param("startTime") Long startTime,
                   @Param("endTime") Long endTime);

    /**
     * 扫描超时未完成任务（F-2011 GetTimedOutUnfinishedTasks）。
     *
     * <p>命中条件 PRD AT-3 §超时扫描：{@code progress != '100%' AND status NOT IN(FAILURE,SUCCESS)
     * AND submit_time < cutoff}。按 submit_time 升序（先处理最早的）。</p>
     *
     * @param cutoff   截止时间（epoch 秒）
     * @param pageable 批次大小
     * @return 超时候选实体
     */
    @Query("""
            SELECT t FROM TaskPO t
            WHERE (t.progress IS NULL OR t.progress <> '100%')
              AND t.status NOT IN ('FAILURE', 'SUCCESS')
              AND t.submitTime IS NOT NULL
              AND t.submitTime < :cutoff
            ORDER BY t.submitTime ASC
            """)
    List<TaskPO> findTimedOut(@Param("cutoff") long cutoff, Pageable pageable);
}
