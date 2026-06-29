package com.nexa.infrastructure.task.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexa.domain.task.exception.TaskPersistenceException;
import com.nexa.domain.task.model.Task;
import com.nexa.domain.task.repository.TaskRepository;
import com.nexa.domain.task.vo.BillingContext;
import com.nexa.domain.task.vo.TaskPlatform;
import com.nexa.domain.task.vo.TaskQuery;
import com.nexa.domain.task.vo.TaskStatus;
import com.nexa.infrastructure.task.persistence.entity.TaskJpaEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link TaskRepository} 的 JPA 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link SpringDataTaskJpaRepository} + 聚合↔实体映射
 * 实现（backend-engineer §2.3）。领域聚合 {@link Task} 与 JPA 实体分离，映射集中于此，domain 不感知
 * Hibernate / Jackson。</p>
 *
 * <p><b>CAS 落地</b>（F-2002）：{@link #updateWithStatus} 委托 Spring Data 的 {@code @Modifying UPDATE
 * ... WHERE status=:fromStatus}，返回受影响行数 >0 即赢得更新（PRD AT-1 §CAS 守卫）。</p>
 *
 * <p><b>BillingContext ⇄ privateData</b>：计费上下文（退款依据）随任务持久化在 {@code private_data}
 * JSONB 的 {@code billing_context} 键下（含 Key 等隐私同处，禁下发用户）。序列化失败包装为
 * {@link TaskPersistenceException}（不吞错）。</p>
 */
@Repository
public class TaskRepositoryImpl implements TaskRepository {

    /** privateData JSON 中承载计费上下文的键名。 */
    private static final String BILLING_CONTEXT_KEY = "billing_context";

    private final SpringDataTaskJpaRepository jpa;
    private final ObjectMapper objectMapper;

    /**
     * @param jpa          Spring Data JPA 仓库（infra 内部依赖）
     * @param objectMapper Jackson 序列化器（Spring Boot 容器提供，复用全站配置）
     */
    public TaskRepositoryImpl(SpringDataTaskJpaRepository jpa, ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    @Override
    public Task save(Task task) {
        TaskJpaEntity saved = jpa.save(toEntity(task));
        task.assignId(saved.getId());
        return toDomain(saved);
    }

    /** {@inheritDoc} */
    @Override
    public boolean updateWithStatus(Task task, TaskStatus fromStatus) {
        if (task.id() == null) {
            throw new TaskPersistenceException("cannot CAS-update a task without id");
        }
        // CAS：WHERE status=fromStatus；返回受影响行数 >0 = 赢得更新（PRD AT-1 §CAS）。
        int affected = jpa.updateWithStatus(
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
        return jpa.findById(id).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Task> findByTaskId(String taskId) {
        return jpa.findByTaskId(taskId).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Task> findByTaskIdAndUserId(String taskId, int userId) {
        return jpa.findByTaskIdAndUserId(taskId, userId).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<Task> findAll(TaskQuery q) {
        Pageable pageable = PageRequest.of(q.page() - 1, q.pageSize());
        return jpa.findPage(q.taskId(), q.userId(), q.channelId(), q.platform(), q.action(),
                        q.status(), q.startTime(), q.endTime(), pageable)
                .stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public long countAll(TaskQuery q) {
        return jpa.countPage(q.taskId(), q.userId(), q.channelId(), q.platform(), q.action(),
                q.status(), q.startTime(), q.endTime());
    }

    /** {@inheritDoc} */
    @Override
    public List<Task> findTimedOut(long cutoffEpochSec, int limit) {
        Pageable pageable = PageRequest.of(0, Math.max(1, limit));
        return jpa.findTimedOut(cutoffEpochSec, pageable).stream().map(this::toDomain).toList();
    }

    // ---- 聚合 <-> 实体映射（基础设施层内部，领域不可见） ----

    /**
     * 领域聚合 → JPA 实体（持久化方向）。计费上下文写入 privateData 的 billing_context 键。
     *
     * @param t 任务聚合
     * @return 待持久化的 JPA 实体
     */
    private TaskJpaEntity toEntity(Task t) {
        TaskJpaEntity e = new TaskJpaEntity();
        e.setId(t.id());
        e.setTaskId(t.taskId());
        e.setPlatform(t.platform() == null ? null : t.platform().toWire());
        e.setUserId(t.userId());
        e.setGroup(t.group());
        e.setChannelId(t.channelId());
        e.setQuota(t.quota());
        e.setAction(t.action());
        e.setStatus(t.status().toWire());
        e.setFailReason(t.failReason());
        e.setSubmitTime(t.submitTime());
        e.setStartTime(t.startTime());
        e.setFinishTime(t.finishTime());
        e.setProgress(t.progress());
        e.setProperties(t.properties());
        e.setData(t.data());
        e.setPrivateData(mergeBillingIntoPrivateData(t.privateData(), t.billingContext()));
        e.setCreatedAt(t.createdAt());
        e.setUpdatedAt(t.updatedAt());
        return e;
    }

    /**
     * JPA 实体 → 领域聚合（重建方向，走 {@link Task#rehydrate}）。从 privateData 解析计费上下文。
     *
     * @param e JPA 实体
     * @return 重建的任务聚合
     */
    private Task toDomain(TaskJpaEntity e) {
        // 具名链式装配：每个 getter 对位到自解释的 Builder 方法，避免 20 位位置参数误位。
        return Task.builder()
                .id(e.getId())
                .taskId(e.getTaskId())
                .platform(TaskPlatform.fromWire(e.getPlatform()))
                .userId(e.getUserId())
                .group(e.getGroup())
                .channelId(e.getChannelId())
                .quota(e.getQuota())
                .action(e.getAction())
                .status(TaskStatus.fromWire(e.getStatus()))
                .failReason(e.getFailReason())
                .submitTime(e.getSubmitTime())
                .startTime(e.getStartTime())
                .finishTime(e.getFinishTime())
                .progress(e.getProgress())
                .properties(e.getProperties())
                .data(e.getData())
                .privateData(e.getPrivateData())
                .billingContext(extractBillingFromPrivateData(e.getPrivateData()))
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    /**
     * 把计费上下文合并进 privateData JSON 的 billing_context 键（持久化时）。
     *
     * @param rawPrivateData 原 privateData JSON（可空）
     * @param ctx            计费上下文（可空）
     * @return 合并后的 privateData JSON 串
     * @throws TaskPersistenceException 序列化失败（保留错误链，不吞错）
     */
    private String mergeBillingIntoPrivateData(String rawPrivateData, BillingContext ctx) {
        if (ctx == null) {
            return rawPrivateData;
        }
        try {
            ObjectNode root;
            if (rawPrivateData == null || rawPrivateData.isBlank()) {
                root = objectMapper.createObjectNode();
            } else {
                JsonNode parsed = objectMapper.readTree(rawPrivateData);
                root = parsed.isObject() ? (ObjectNode) parsed : objectMapper.createObjectNode();
            }
            ObjectNode bc = objectMapper.createObjectNode();
            bc.put("per_call_billing", ctx.perCallBilling());
            bc.put("billing_source", ctx.billingSource() == null ? null : ctx.billingSource().toWire());
            bc.put("pre_consumed_quota", ctx.preConsumedQuota());
            if (ctx.actualTokens() != null) {
                bc.put("actual_tokens", ctx.actualTokens());
            }
            root.set(BILLING_CONTEXT_KEY, bc);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new TaskPersistenceException("serialize billing context into private_data failed", ex);
        }
    }

    /**
     * 从 privateData JSON 解析计费上下文（重建时）。无 billing_context 键返回 null。
     *
     * @param rawPrivateData privateData JSON（可空）
     * @return 计费上下文（可空）
     * @throws TaskPersistenceException 反序列化失败（保留错误链）
     */
    private BillingContext extractBillingFromPrivateData(String rawPrivateData) {
        if (rawPrivateData == null || rawPrivateData.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(rawPrivateData);
            JsonNode bc = root.get(BILLING_CONTEXT_KEY);
            if (bc == null || !bc.isObject()) {
                return null;
            }
            boolean perCall = bc.path("per_call_billing").asBoolean(false);
            BillingContext.BillingSource source = BillingContext.BillingSource.fromWire(
                    bc.path("billing_source").asText(null));
            long pre = bc.path("pre_consumed_quota").asLong(0L);
            Long actual = bc.has("actual_tokens") ? bc.get("actual_tokens").asLong() : null;
            return new BillingContext(perCall, source, pre, actual);
        } catch (Exception ex) {
            throw new TaskPersistenceException("deserialize billing context from private_data failed", ex);
        }
    }
}
