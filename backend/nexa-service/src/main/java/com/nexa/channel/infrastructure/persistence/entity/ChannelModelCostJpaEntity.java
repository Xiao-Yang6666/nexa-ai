package com.nexa.channel.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * 供应商成本倍率 JPA 持久化实体（基础设施层，对齐 V14 {@code channel_model_costs}，DB-SCHEMA §19）。
 */
@Entity
@Table(name = "channel_model_costs", indexes = {
        @Index(name = "idx_channel_model_costs_channel_id", columnList = "channel_id"),
        @Index(name = "idx_channel_model_costs_deleted_at", columnList = "deleted_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_channel_model", columnNames = {"channel_id", "upstream_model"})
})
@SQLRestriction("deleted_at IS NULL")
public class ChannelModelCostJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id")
    private Integer channelId;

    @Column(name = "upstream_model", nullable = false, length = 255)
    private String upstreamModel;

    @Column(name = "cost_ratio", columnDefinition = "numeric default 0")
    private BigDecimal costRatio;

    @Column(name = "completion_cost_ratio", columnDefinition = "numeric default 0")
    private BigDecimal completionCostRatio;

    @Column(name = "enabled", columnDefinition = "boolean default true")
    private Boolean enabled;

    @Column(name = "effective_time", columnDefinition = "bigint default 0")
    private Long effectiveTime;

    @Column(name = "source_unit_price", columnDefinition = "numeric default 0")
    private BigDecimal sourceUnitPrice;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_time")
    private Long createdTime;

    @Column(name = "updated_time")
    private Long updatedTime;

    @Column(name = "deleted_at")
    private Long deletedAt;

    /** JPA 规范要求的无参构造器。 */
    public ChannelModelCostJpaEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getChannelId() { return channelId; }
    public void setChannelId(Integer channelId) { this.channelId = channelId; }
    public String getUpstreamModel() { return upstreamModel; }
    public void setUpstreamModel(String upstreamModel) { this.upstreamModel = upstreamModel; }
    public BigDecimal getCostRatio() { return costRatio; }
    public void setCostRatio(BigDecimal costRatio) { this.costRatio = costRatio; }
    public BigDecimal getCompletionCostRatio() { return completionCostRatio; }
    public void setCompletionCostRatio(BigDecimal completionCostRatio) { this.completionCostRatio = completionCostRatio; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Long getEffectiveTime() { return effectiveTime; }
    public void setEffectiveTime(Long effectiveTime) { this.effectiveTime = effectiveTime; }
    public BigDecimal getSourceUnitPrice() { return sourceUnitPrice; }
    public void setSourceUnitPrice(BigDecimal sourceUnitPrice) { this.sourceUnitPrice = sourceUnitPrice; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getCreatedTime() { return createdTime; }
    public void setCreatedTime(Long createdTime) { this.createdTime = createdTime; }
    public Long getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(Long updatedTime) { this.updatedTime = updatedTime; }
    public Long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
}
