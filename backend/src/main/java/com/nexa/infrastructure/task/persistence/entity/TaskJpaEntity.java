package com.nexa.infrastructure.task.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 任务 JPA 持久化实体（基础设施层，对齐 V14 {@code tasks} 与 DB-SCHEMA §9）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.task.model.Task} 分离（DDD：domain 不感知 JPA）。
 * 映射转换在 {@code TaskRepositoryImpl}。三个 JSON 字段（{@code properties}/{@code data}/
 * {@code privateData}）用 Hibernate 6 {@code @JdbcTypeCode(SqlTypes.JSON)} 以 String 承载 JSONB
 * （DB-SCHEMA §9）。{@code privateData} 含上游 Key 等隐私，<b>禁止下发用户</b>（仅库内）。
 * {@code group} 为 PG 保留字，列名双引号转义。索引对齐 DB-SCHEMA §9。</p>
 */
@Entity
@Table(name = "tasks", indexes = {
        @Index(name = "idx_tasks_task_id", columnList = "task_id"),
        @Index(name = "idx_tasks_platform", columnList = "platform"),
        @Index(name = "idx_tasks_user_id", columnList = "user_id"),
        @Index(name = "idx_tasks_channel_id", columnList = "channel_id"),
        @Index(name = "idx_tasks_action", columnList = "action"),
        @Index(name = "idx_tasks_status", columnList = "status"),
        @Index(name = "idx_tasks_submit_time", columnList = "submit_time"),
        @Index(name = "idx_tasks_start_time", columnList = "start_time"),
        @Index(name = "idx_tasks_finish_time", columnList = "finish_time")
})
public class TaskJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", columnDefinition = "varchar(191)")
    private String taskId;

    @Column(columnDefinition = "varchar(30)")
    private String platform;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "\"group\"", columnDefinition = "varchar(50)")
    private String group;

    @Column(name = "channel_id")
    private Integer channelId;

    @Column
    private Long quota;

    @Column(columnDefinition = "varchar(40)")
    private String action;

    @Column(columnDefinition = "varchar(20)")
    private String status;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(name = "submit_time")
    private Long submitTime;

    @Column(name = "start_time")
    private Long startTime;

    @Column(name = "finish_time")
    private Long finishTime;

    @Column(columnDefinition = "varchar(20)")
    private String progress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String properties;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String data;

    /** 含上游 Key 等隐私，禁止下发用户（PRD AT-4 §Key 禁返）。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "private_data", columnDefinition = "jsonb")
    private String privateData;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;

    /** JPA 规范要求的无参构造器。 */
    public TaskJpaEntity() {
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
}
