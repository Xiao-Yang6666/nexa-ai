package com.nexa.infrastructure.task.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.task.persistence.po.TaskPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 异步任务 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（insert/selectById/selectOne/selectList/selectPage/
 * selectCount...）。仅供 {@link TaskRepositoryImpl} 内部使用，领域只认
 * {@code domain.repository.TaskRepository}。动态过滤分页（findPage/countPage）、超时扫描
 * （findTimedOut）、按 task_id 查询等派生查询由 Impl 内 {@code LambdaQueryWrapper} 组装，不在此声明。</p>
 *
 * <p>仅 CAS 条件更新（F-2002 UpdateWithStatus）因带 {@code WHERE status=:fromStatus} 守卫且一次性
 * 写全部可变字段，用 {@link #updateWithStatus} 的 {@code @Update} 注解显式声明（取代原 Spring Data
 * {@code @Modifying UPDATE}），SQL 直写 DB 列名（非字段名）。</p>
 */
public interface TaskMapper extends BaseMapper<TaskPO> {

    /**
     * CAS 条件更新（F-2002 UpdateWithStatus）。
     *
     * <p>PRD AT-1 §CAS：以 {@code fromStatus} 为 WHERE 守卫 UPDATE，仅当库中当前状态 = fromStatus
     * 时才更新（返回受影响行数 1）；被他进程改写时当前状态≠fromStatus → 受影响 0（CAS 失败）。
     * 一次性写全部可变字段（status/fail_reason/start_time/finish_time/progress/data/updated_at），
     * 避免多次往返。SQL 用 DB 列名（与原 JPQL 的字段名等价映射）。</p>
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
    @Update("""
            UPDATE tasks SET
                status = #{toStatus},
                fail_reason = #{failReason},
                start_time = #{startTime},
                finish_time = #{finishTime},
                progress = #{progress},
                data = #{data,typeHandler=com.nexa.infrastructure.persistence.JsonbStringTypeHandler},
                updated_at = #{updatedAt}
            WHERE id = #{id} AND status = #{fromStatus}
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
}
