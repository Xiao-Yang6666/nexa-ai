package com.nexa.infrastructure.task.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexa.domain.task.exception.TaskPersistenceException;
import com.nexa.domain.task.model.Task;
import com.nexa.domain.task.vo.BillingContext;
import com.nexa.domain.task.vo.TaskPlatform;
import com.nexa.domain.task.vo.TaskStatus;

/**
 * 任务持久化实体（基础设施层，对齐 V14 {@code tasks} 与 DB-SCHEMA §9）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.task.model.Task} 分离（DDD：domain 不感知持久化）。
 * 映射由本类就近工厂方法 {@link #toDomain(ObjectMapper)} / {@link #of(Task, ObjectMapper)} 承载
 * （含计费上下文 ⇄ privateData 的 Jackson 合并/解析，故需外部 {@link ObjectMapper} 协作者）。
 * 三个 JSON 字段（{@code properties}/{@code data}/{@code privateData}）用 Hibernate 6
 * {@code @JdbcTypeCode(SqlTypes.JSON)} 以 String 承载 JSONB（DB-SCHEMA §9）。{@code privateData}
 * 含上游 Key 等隐私，<b>禁止下发用户</b>（仅库内）。{@code group} 为 PG 保留字，列名双引号转义。
 * 索引对齐 DB-SCHEMA §9。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：本 PO 同时保留 JPA 注解（{@code @Entity/@Table/@Column/@Id}，
 * 满足并存期 {@code ddl-auto=validate} 全局启动校验）与 MyBatis-Plus 注解
 * （{@code @TableName/@TableId/@TableField}，供 Mapper 实际读写）。两套注解命名空间独立、互不读取。
 * 阶段4 统一移除 JPA 注解。</p>
 */
@TableName(value = "tasks", autoResultMap = true)
public class TaskPO {

    /** privateData JSON 中承载计费上下文的键名。 */
    private static final String BILLING_CONTEXT_KEY = "billing_context";

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private String taskId;

    @TableField("platform")
    private String platform;

    @TableField("user_id")
    private Integer userId;

    @TableField("\"group\"")
    private String group;

    @TableField("channel_id")
    private Integer channelId;

    @TableField("quota")
    private Long quota;

    @TableField("action")
    private String action;

    @TableField("status")
    private String status;

    @TableField("fail_reason")
    private String failReason;

    @TableField("submit_time")
    private Long submitTime;

    @TableField("start_time")
    private Long startTime;

    @TableField("finish_time")
    private Long finishTime;

    @TableField("progress")
    private String progress;

    @TableField(value = "properties", typeHandler = com.nexa.infrastructure.persistence.JsonbStringTypeHandler.class)
    private String properties;

    @TableField(value = "data", typeHandler = com.nexa.infrastructure.persistence.JsonbStringTypeHandler.class)
    private String data;

    /** 含上游 Key 等隐私，禁止下发用户（PRD AT-4 §Key 禁返）。 */
    @TableField(value = "private_data", typeHandler = com.nexa.infrastructure.persistence.JsonbStringTypeHandler.class)
    private String privateData;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;

    /** 框架（JPA / MyBatis-Plus）实例化所需的无参构造器。 */
    public TaskPO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public Integer getChannelId() {
        return channelId;
    }

    public void setChannelId(Integer channelId) {
        this.channelId = channelId;
    }

    public Long getQuota() {
        return quota;
    }

    public void setQuota(Long quota) {
        this.quota = quota;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public Long getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(Long submitTime) {
        this.submitTime = submitTime;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(Long finishTime) {
        this.finishTime = finishTime;
    }

    public String getProgress() {
        return progress;
    }

    public void setProgress(String progress) {
        this.progress = progress;
    }

    public String getProperties() {
        return properties;
    }

    public void setProperties(String properties) {
        this.properties = properties;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getPrivateData() {
        return privateData;
    }

    public void setPrivateData(String privateData) {
        this.privateData = privateData;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * 领域聚合 → PO（持久化方向）。计费上下文写入 privateData 的 billing_context 键。
     *
     * @param t            任务聚合（非空）
     * @param objectMapper Jackson 序列化器（计费上下文合并进 privateData 用）
     * @return 待持久化的 PO
     */
    public static TaskPO of(Task t, ObjectMapper objectMapper) {
        TaskPO e = new TaskPO();
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
        e.setPrivateData(mergeBillingIntoPrivateData(objectMapper, t.privateData(), t.billingContext()));
        e.setCreatedAt(t.createdAt());
        e.setUpdatedAt(t.updatedAt());
        return e;
    }

    /**
     * PO → 领域聚合（重建方向，走 {@link Task#rehydrate} 链式装配）。从 privateData 解析计费上下文。
     *
     * @param objectMapper Jackson 反序列化器（从 privateData 解析计费上下文用）
     * @return 重建的任务聚合
     */
    public Task toDomain(ObjectMapper objectMapper) {
        // 具名链式装配：每个 getter 对位到自解释的 Builder 方法，避免 20 位位置参数误位。
        return Task.builder()
                .id(id)
                .taskId(taskId)
                .platform(TaskPlatform.fromWire(platform))
                .userId(userId)
                .group(group)
                .channelId(channelId)
                .quota(quota)
                .action(action)
                .status(TaskStatus.fromWire(status))
                .failReason(failReason)
                .submitTime(submitTime)
                .startTime(startTime)
                .finishTime(finishTime)
                .progress(progress)
                .properties(properties)
                .data(data)
                .privateData(privateData)
                .billingContext(extractBillingFromPrivateData(objectMapper, privateData))
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    /**
     * 把计费上下文合并进 privateData JSON 的 billing_context 键（持久化时）。
     *
     * @param objectMapper   Jackson 序列化器
     * @param rawPrivateData 原 privateData JSON（可空）
     * @param ctx            计费上下文（可空）
     * @return 合并后的 privateData JSON 串
     * @throws TaskPersistenceException 序列化失败（保留错误链，不吞错）
     */
    private static String mergeBillingIntoPrivateData(ObjectMapper objectMapper,
                                                      String rawPrivateData, BillingContext ctx) {
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
     * @param objectMapper   Jackson 反序列化器
     * @param rawPrivateData privateData JSON（可空）
     * @return 计费上下文（可空）
     * @throws TaskPersistenceException 反序列化失败（保留错误链）
     */
    private static BillingContext extractBillingFromPrivateData(ObjectMapper objectMapper,
                                                                String rawPrivateData) {
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
