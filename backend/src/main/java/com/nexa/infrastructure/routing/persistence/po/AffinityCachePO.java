package com.nexa.infrastructure.routing.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.routing.vo.AffinityCacheEntry;
import com.nexa.domain.routing.vo.AffinityCacheKey;

import java.time.Instant;

/**
 * 亲和缓存条目持久化实体（基础设施层，对齐 V9 {@code affinity_cache}，F-2029/F-2032/F-2033）。
 *
 * <p>与领域值对象 {@link com.nexa.domain.routing.vo.AffinityCacheEntry} 分离，映射由本类的就近工厂方法
 * {@link #toDomain()} / {@link #of(AffinityCacheKey, AffinityCacheEntry)} 承载。复合唯一键 (rule_name,
 * key_fingerprint, COALESCE(using_group,'')) 在 V9 用表达式索引实现，仓储层 upsert 时按三元组先查后写。</p>
 *
 * <p>{@code last_hit_at}/{@code expires_at} 用 epoch 秒（BIGINT），与领域 {@link java.time.Instant} 互转。
 * {@code key_fingerprint} 落 SHA-256 前 16 字节 hex（32 字符），不存原始会话键避免 PII 落库。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：本 PO 同时保留 JPA 注解（{@code @Entity/@Table/@Column/@Id}，
 * 满足并存期 {@code ddl-auto=validate} 全局启动校验）与 MyBatis-Plus 注解
 * （{@code @TableName/@TableId/@TableField}，供 Mapper 实际读写）。两套注解命名空间独立、互不读取。
 * 阶段4 统一移除 JPA 注解。</p>
 */
@TableName("affinity_cache")
public class AffinityCachePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("rule_name")
    private String ruleName;

    @TableField("key_fingerprint")
    private String keyFingerprint;

    @TableField("using_group")
    private String usingGroup;

    @TableField("channel_id")
    private long channelId;

    @TableField("hit_count")
    private long hitCount;

    @TableField("last_hit_at")
    private long lastHitAt;

    @TableField("expires_at")
    private long expiresAt;

    /** 框架（JPA / MyBatis-Plus）实例化所需的无参构造器。 */
    public AffinityCachePO() {
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

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * PO → 领域值对象（重建方向）：仅取缓存语义字段，{@code last_hit_at}/{@code expires_at} epoch 秒还原为
     * {@link Instant}。三元组键字段（rule_name/key_fingerprint/using_group）由调用方持有，不进缓存值对象。
     *
     * @return 重建的亲和缓存条目
     */
    public AffinityCacheEntry toDomain() {
        return new AffinityCacheEntry(
                channelId, hitCount,
                Instant.ofEpochSecond(lastHitAt),
                Instant.ofEpochSecond(expiresAt));
    }

    /**
     * 三元组键 + 缓存值 → PO（持久化方向）：键字段取自 {@link AffinityCacheKey}，命中/时间字段取自
     * {@link AffinityCacheEntry}，{@link Instant} 落 epoch 秒。新建 PO（id 为空），upsert 的 id 回填由仓储层负责。
     *
     * @param key   三元组缓存键（非空）
     * @param entry 缓存值（非空）
     * @return 待持久化的 PO
     */
    public static AffinityCachePO of(AffinityCacheKey key, AffinityCacheEntry entry) {
        AffinityCachePO po = new AffinityCachePO();
        po.ruleName = key.ruleName();
        po.keyFingerprint = key.fingerprint();
        po.usingGroup = key.usingGroup();
        po.channelId = entry.channelId();
        po.hitCount = entry.hitCount();
        po.lastHitAt = entry.lastHitAt().getEpochSecond();
        po.expiresAt = entry.expiresAt().getEpochSecond();
        return po;
    }
}
