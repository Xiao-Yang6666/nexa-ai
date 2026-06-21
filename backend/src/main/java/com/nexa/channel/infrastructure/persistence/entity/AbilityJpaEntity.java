package com.nexa.channel.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Ability 路由索引 JPA 实体（基础设施层，V25 {@code abilities} 表，DB-SCHEMA §4）。
 *
 * <p>承载 (group, model, channel_id) 复合主键的「分组×模型→渠道」反向索引，CH-2 选渠主查询目标。
 * 渠道 CRUD 时由 {@code ChannelRepositoryImpl} fan-out/fan-in 维护本表——不直接对外暴露，
 * 由 channel BC 的 {@code ChannelSelectionAdapter} 查询驱动 CH-2/CH-5。</p>
 *
 * <p>DDD：本实体只在基础设施层使用，不建独立领域聚合（ability 是 channel 的派生索引，
 * 非独立聚合根）；选渠 VO {@code ChannelCandidate} 是路由 BC 的输出模型。</p>
 */
@Entity
@Table(name = "abilities", indexes = {
        @Index(name = "idx_abilities_group_model", columnList = "\"group\", model, enabled"),
        @Index(name = "idx_abilities_channel", columnList = "channel_id"),
        @Index(name = "idx_abilities_tag", columnList = "tag")
})
@IdClass(AbilityJpaEntity.AbilityPK.class)
public class AbilityJpaEntity {

    @Id
    @Column(name = "\"group\"", length = 64, nullable = false)
    private String group;

    @Id
    @Column(name = "model", length = 255, nullable = false)
    private String model;

    @Id
    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "priority", nullable = false)
    private long priority = 0L;

    @Column(name = "weight", nullable = false)
    private int weight = 0;

    @Column(name = "tag", length = 255)
    private String tag;

    /** JPA 无参构造器。 */
    public AbilityJpaEntity() {
    }

    public AbilityJpaEntity(String group, String model, Long channelId, boolean enabled,
                            long priority, int weight, String tag) {
        this.group = group;
        this.model = model;
        this.channelId = channelId;
        this.enabled = enabled;
        this.priority = priority;
        this.weight = weight;
        this.tag = tag;
    }

    // ---- 访问器 ----

    public String getGroup() {
        return group;
    }

    public String getModel() {
        return model;
    }

    public Long getChannelId() {
        return channelId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getPriority() {
        return priority;
    }

    public int getWeight() {
        return weight;
    }

    public String getTag() {
        return tag;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPriority(long priority) {
        this.priority = priority;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    /** 复合主键类（JPA IdClass 要求）。 */
    public static class AbilityPK implements Serializable {
        private static final long serialVersionUID = 1L;
        private String group;
        private String model;
        private Long channelId;

        public AbilityPK() {
        }

        public AbilityPK(String group, String model, Long channelId) {
            this.group = group;
            this.model = model;
            this.channelId = channelId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AbilityPK that)) return false;
            return Objects.equals(group, that.group)
                    && Objects.equals(model, that.model)
                    && Objects.equals(channelId, that.channelId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(group, model, channelId);
        }

        public String getGroup() { return group; }
        public String getModel() { return model; }
        public Long getChannelId() { return channelId; }
        public void setGroup(String group) { this.group = group; }
        public void setModel(String model) { this.model = model; }
        public void setChannelId(Long channelId) { this.channelId = channelId; }
    }
}
