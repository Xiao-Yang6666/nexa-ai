package com.nexa.infrastructure.routing.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 亲和缓存条目 JPA 持久化实体（基础设施层，对齐 V9 {@code affinity_cache}，F-2029/F-2032/F-2033）。
 *
 * <p>与领域值对象 {@link com.nexa.domain.routing.vo.AffinityCacheEntry} 分离。复合唯一键 (rule_name,
 * key_fingerprint, COALESCE(using_group,'')) 在 V9 用表达式索引实现（JPA 不直接表达），仓储层 upsert
 * 时按三元组先查后写。</p>
 *
 * <p>{@code last_hit_at}/{@code expires_at} 用 epoch 秒（BIGINT），与领域 {@link java.time.Instant} 在仓储层互转。
 * {@code key_fingerprint} 落 SHA-256 前 16 字节 hex（32 字符），不存原始会话键避免 PII 落库。</p>
 */
@Entity
@Table(name = "affinity_cache", indexes = {
        @Index(name = "idx_affinity_cache_rule", columnList = "rule_name"),
        @Index(name = "idx_affinity_cache_expires_at", columnList = "expires_at")
})
public class AffinityCacheJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false, length = 64)
    private String ruleName;

    @Column(name = "key_fingerprint", nullable = false, length = 64)
    private String keyFingerprint;

    @Column(name = "using_group", length = 64)
    private String usingGroup;

    @Column(name = "channel_id", nullable = false)
    private long channelId;

    @Column(name = "hit_count", nullable = false)
    private long hitCount;

    @Column(name = "last_hit_at", nullable = false)
    private long lastHitAt;

    @Column(name = "expires_at", nullable = false)
    private long expiresAt;

    public AffinityCacheJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getKeyFingerprint() {
        return keyFingerprint;
    }

    public void setKeyFingerprint(String keyFingerprint) {
        this.keyFingerprint = keyFingerprint;
    }

    public String getUsingGroup() {
        return usingGroup;
    }

    public void setUsingGroup(String usingGroup) {
        this.usingGroup = usingGroup;
    }

    public long getChannelId() {
        return channelId;
    }

    public void setChannelId(long channelId) {
        this.channelId = channelId;
    }

    public long getHitCount() {
        return hitCount;
    }

    public void setHitCount(long hitCount) {
        this.hitCount = hitCount;
    }

    public long getLastHitAt() {
        return lastHitAt;
    }

    public void setLastHitAt(long lastHitAt) {
        this.lastHitAt = lastHitAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
