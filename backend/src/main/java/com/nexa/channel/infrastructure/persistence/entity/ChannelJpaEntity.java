package com.nexa.channel.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * 渠道 JPA 持久化实体（基础设施层，对齐 V7 {@code channels} 与 DB-SCHEMA §3）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.channel.domain.model.Channel} 分离（DDD：domain 不感知 JPA）。
 * 映射转换在 {@code ChannelRepositoryImpl}。{@code key} 落库但绝不进视图 DTO。</p>
 *
 * <p>JSON 字段（model_mapping/setting/channel_info）用 Hibernate 6 {@code @JdbcTypeCode(SqlTypes.JSON)}
 * 以 String 承载 JSONB；channel_info 在仓储层与领域值对象 {@code ChannelInfo} 互转（Jackson）。
 * {@code key}/{@code group} 为 PG 保留字，列名加双引号转义（与 V7 一致）。</p>
 */
@Entity
@Table(name = "channels", indexes = {
        @Index(name = "idx_channels_name", columnList = "name"),
        @Index(name = "idx_channels_tag", columnList = "tag")
})
public class ChannelJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type", nullable = false)
    private int type;

    @Column(name = "\"key\"", nullable = false, columnDefinition = "text")
    private String key;

    @Column(name = "status", nullable = false)
    private int status;

    @Column(name = "name")
    private String name;

    @Column(name = "weight", nullable = false)
    private int weight;

    @Column(name = "base_url", nullable = false, columnDefinition = "text")
    private String baseUrl;

    @Column(name = "models", nullable = false, columnDefinition = "text")
    private String models;

    @Column(name = "\"group\"", nullable = false, length = 64)
    private String group;

    @Column(name = "priority", nullable = false)
    private long priority;

    @Column(name = "auto_ban", nullable = false)
    private int autoBan;

    @Column(name = "balance", nullable = false, precision = 30, scale = 6)
    private BigDecimal balance;

    @Column(name = "used_quota", nullable = false)
    private long usedQuota;

    @Column(name = "response_time")
    private Integer responseTime;

    @Column(name = "test_time")
    private Long testTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_mapping", columnDefinition = "jsonb")
    private String modelMapping;

    @Column(name = "status_code_mapping", nullable = false, length = 1024)
    private String statusCodeMapping;

    @Column(name = "tag")
    private String tag;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "setting", columnDefinition = "jsonb")
    private String setting;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channel_info", columnDefinition = "jsonb")
    private String channelInfo;

    @Column(name = "created_time")
    private Long createdTime;

    /** JPA 规范要求的无参构造器。 */
    public ChannelJpaEntity() {
    }

    // ---- 访问器（JPA 需要 getter/setter；领域逻辑不在此） ----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModels() {
        return models;
    }

    public void setModels(String models) {
        this.models = models;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public long getPriority() {
        return priority;
    }

    public void setPriority(long priority) {
        this.priority = priority;
    }

    public int getAutoBan() {
        return autoBan;
    }

    public void setAutoBan(int autoBan) {
        this.autoBan = autoBan;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public long getUsedQuota() {
        return usedQuota;
    }

    public void setUsedQuota(long usedQuota) {
        this.usedQuota = usedQuota;
    }

    public Integer getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(Integer responseTime) {
        this.responseTime = responseTime;
    }

    public Long getTestTime() {
        return testTime;
    }

    public void setTestTime(Long testTime) {
        this.testTime = testTime;
    }

    public String getModelMapping() {
        return modelMapping;
    }

    public void setModelMapping(String modelMapping) {
        this.modelMapping = modelMapping;
    }

    public String getStatusCodeMapping() {
        return statusCodeMapping;
    }

    public void setStatusCodeMapping(String statusCodeMapping) {
        this.statusCodeMapping = statusCodeMapping;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getSetting() {
        return setting;
    }

    public void setSetting(String setting) {
        this.setting = setting;
    }

    public String getChannelInfo() {
        return channelInfo;
    }

    public void setChannelInfo(String channelInfo) {
        this.channelInfo = channelInfo;
    }

    public Long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Long createdTime) {
        this.createdTime = createdTime;
    }
}
