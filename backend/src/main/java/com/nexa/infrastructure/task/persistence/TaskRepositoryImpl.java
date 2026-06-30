package com.nexa.infrastructure.task.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.domain.task.exception.TaskPersistenceException;
import com.nexa.domain.task.model.Task;
import com.nexa.domain.task.repository.TaskRepository;
import com.nexa.domain.task.vo.TaskQuery;
import com.nexa.domain.task.vo.TaskStatus;
import com.nexa.infrastructure.persistence.PageQueries;
import com.nexa.infrastructure.task.persistence.po.TaskPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link TaskRepository} 的 MyBatis-Plus 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link TaskMapper} + PO 就近工厂方法
 * （{@code TaskPO.of} / {@code po.toDomain}）实现。领域聚合 {@link Task} 与 PO 分离，映射收敛在 PO，
 * domain 不感知 MyBatis-Plus / Jackson。</p>
 *
 * <p><b>CAS 落地</b>（F-2002）：{@link #updateWithStatus} 委托 {@link TaskMapper#updateWithStatus}
 * 的 {@code @Update UPDATE ... WHERE status=:fromStatus}，返回受影响行数 >0 即赢得更新
 * （PRD AT-1 §CAS 守卫）。</p>
 *
 * <p><b>BillingContext ⇄ privateData</b>：计费上下文（退款依据）随任务持久化在 {@code private_data}
 * JSONB 的 {@code billing_context} 键下（含 Key 等隐私同处，禁下发用户）。序列化逻辑收敛在 PO 工厂方法，
 * 失败包装为 {@link TaskPersistenceException}（不吞错）。{@link ObjectMapper} 作为协作者传入 PO 工厂方法。</p>
 */
@Repository
public class TaskRepositoryImpl implements TaskRepository {

    private final TaskMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * @param mapper       MyBatis-Plus Mapper（infra 内部依赖）
     * @param objectMapper Jackson 序列化器（Spring Boot 容器提供，复用全站配置）
     */
    public TaskRepositoryImpl(TaskMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    @Override
    public Task save(Task task) {
        TaskPO po = TaskPO.of(task, objectMapper);
        mapper.insert(po);              // 回填自增 id 到 po
        task.assignId(po.getId());
        return po.toDomain(objectMapper);
    }

    /** {@inheritDoc} */
    @Override
    public boolean updateWithStatus(Task task, TaskStatus fromStatus) {
        if (task.id() == null) {
            throw new TaskPersistenceException("cannot CAS-update a task without id");
        }
        // CAS：WHERE status=fromStatus；返回受影响行数 >0 = 赢得更新（PRD AT-1 §CAS）。
        int affected = mapper.updateWithStatus(
                task.id(),
                fromStatus == null ? null : fromStatus.toWire(),
                task.status().toWire(),
                task.failReason(),
                task.startTime(),
                task.finishTime(),
                task.progress(),
                task.data(),
                task.updatedAt());
        return affected > 0;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Task> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(po -> po.toDomain(objectMapper));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Task> findByTaskId(String taskId) {
        LambdaQueryWrapper<TaskPO> w = Wrappers.<TaskPO>lambdaQuery()
                .eq(TaskPO::getTaskId, taskId);
        return Optional.ofNullable(mapper.selectOne(w)).map(po -> po.toDomain(objectMapper));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Task> findByTaskIdAndUserId(String taskId, int userId) {
        LambdaQueryWrapper<TaskPO> w = Wrappers.<TaskPO>lambdaQuery()
                .eq(TaskPO::getTaskId, taskId)
                .eq(TaskPO::getUserId, userId);
        return Optional.ofNullable(mapper.selectOne(w)).map(po -> po.toDomain(objectMapper));
    }

    /** {@inheritDoc} */
    @Override
    public List<Task> findAll(TaskQuery q) {
        Page<TaskPO> page = PageQueries.mpOf(q.page(), q.pageSize());
        return mapper.selectPage(page, filterWrapper(q).orderByDesc(TaskPO::getId))
                .getRecords().stream()
                .map(po -> po.toDomain(objectMapper))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public long countAll(TaskQuery q) {
        return mapper.selectCount(filterWrapper(q));
    }

    /** {@inheritDoc} */
    @Override
    public List<Task> findTimedOut(long cutoffEpochSec, int limit) {
        int safeLimit = Math.max(1, limit);
        // 命中条件：(progress IS NULL OR progress<>'100%') AND status NOT IN(FAILURE,SUCCESS)
        //          AND submit_time IS NOT NULL AND submit_time < cutoff，按 submit_time 升序。
        // safeLimit 已 clamp 为 ≥1 可信整数，last("LIMIT n") 无注入风险。
        LambdaQueryWrapper<TaskPO> w = Wrappers.<TaskPO>lambdaQuery()
                .and(inner -> inner.isNull(TaskPO::getProgress).or().ne(TaskPO::getProgress, "100%"))
                .notIn(TaskPO::getStatus, List.of("FAILURE", "SUCCESS"))
                .isNotNull(TaskPO::getSubmitTime)
                .lt(TaskPO::getSubmitTime, cutoffEpochSec)
                .orderByAsc(TaskPO::getSubmitTime)
                .last("LIMIT " + safeLimit);
        return mapper.selectList(w).stream()
                .map(po -> po.toDomain(objectMapper))
                .toList();
    }

    /**
     * 构造 findPage/countPage 共用的动态过滤条件（{@code (:p IS NULL OR col=:p)} 惯用法 →
     * {@code .eq(p != null, PO::getCol, p)}）。用户侧 q.userId() 非空强制 self-scope（PRD AT-2），
     * 管理侧可空 = 跨用户。不含排序（findAll 单独追加 orderByDesc(id)）。
     *
     * @param q 查询条件
     * @return 装配好可选过滤项的 wrapper
     */
    private LambdaQueryWrapper<TaskPO> filterWrapper(TaskQuery q) {
        return Wrappers.<TaskPO>lambdaQuery()
                .eq(q.taskId() != null, TaskPO::getTaskId, q.taskId())
                .eq(q.userId() != null, TaskPO::getUserId, q.userId())
                .eq(q.channelId() != null, TaskPO::getChannelId, q.channelId())
                .eq(q.platform() != null, TaskPO::getPlatform, q.platform())
                .eq(q.action() != null, TaskPO::getAction, q.action())
                .eq(q.status() != null, TaskPO::getStatus, q.status())
                .ge(q.startTime() != null, TaskPO::getSubmitTime, q.startTime())
                .le(q.endTime() != null, TaskPO::getSubmitTime, q.endTime());
    }
}
